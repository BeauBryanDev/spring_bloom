package com.springbloom.adapter.in.chat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springbloom.config.SecurityConfig;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ChatUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase.RequestQuotationCommand;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The wire contract of POST /api/chat/quotations. The use case is mocked. */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerQuotationTest {

    private static final String VALID_BODY = """
            {"sessionKey": "demo-1",
             "lines": [
               {"productType": "INDIVIDUAL",
                "species": [{"speciesKey": "rose", "quantity": 3}]},
               {"productType": "BOUQUET", "discountPercentage": 10.00,
                "species": [{"speciesKey": "Carnation", "quantity": 6}]}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestQuotationUseCase requestQuotation;

    @MockitoBean
    private ClassifyFlowerUseCase classifyFlower;

    @MockitoBean
    private ChatUseCase chat;

    private static Quotation sampleQuotation() {
        QuotationItem individual = new QuotationItem(
                ProductType.INDIVIDUAL, null,
                List.of(new QuotationItemSpecies(
                        1L, "Rosa", 3, Money.of("4448.00"), Money.of("13344.00"))),
                Money.of("13344.00"), Money.of("13344.00"));

        QuotationItem bouquet = new QuotationItem(
                ProductType.BOUQUET, new BigDecimal("10.00"),
                List.of(new QuotationItemSpecies(
                        2L, "Clavel", 6, Money.of("1000.00"), Money.of("6000.00"))),
                Money.of("6000.00"), Money.of("5400.00"));

        return Quotation.of(List.of(individual, bouquet))
                .withId(42L)
                .withNumber("COT-20260826-48395")
                .withValidUntil(Instant.parse("2026-09-02T15:00:00Z"));
    }

    @Test
    @DisplayName("a priced quotation comes back 201 with its number and snapshots")
    void returnsThePricedQuotation() throws Exception {
        when(requestQuotation.requestQuotation(any())).thenReturn(sampleQuotation());

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.quotationNumber").value("COT-20260826-48395"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.validUntil").value("2026-09-02T15:00:00Z"))
                .andExpect(jsonPath("$.subtotal").value("19344.00"))
                .andExpect(jsonPath("$.discountAmount").value("600.00"))
                .andExpect(jsonPath("$.totalAmount").value("18744.00"))
                .andExpect(jsonPath("$.totalStems").value(9))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].productType").value("BOUQUET"))
                .andExpect(jsonPath("$.lines[1].discountAmount").value("600.00"))
                .andExpect(jsonPath("$.lines[1].species[0].commonName").value("Clavel"))
                .andExpect(jsonPath("$.lines[1].species[0].unitPrice").value("1000.00"));
    }

    @Test
    @DisplayName("money is quoted as strings, never as JSON numbers")
    void keepsMoneyOutOfBinaryFloats() throws Exception {
        when(requestQuotation.requestQuotation(any())).thenReturn(sampleQuotation());

        String body = mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"totalAmount\":\"18744.00\"");
        assertThat(body).doesNotContain("\"totalAmount\":18744");
    }

    @Test
    @DisplayName("the JSON body is translated into the command the use case expects")
    void buildsTheCommandFromTheBody() throws Exception {
        when(requestQuotation.requestQuotation(any())).thenReturn(sampleQuotation());

        var captor = org.mockito.ArgumentCaptor.forClass(RequestQuotationCommand.class);

        mockMvc.perform(post("/api/chat/quotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        verify(requestQuotation).requestQuotation(captor.capture());
        RequestQuotationCommand command = captor.getValue();

        assertThat(command.sessionKey()).isEqualTo("demo-1");
        assertThat(command.lines()).hasSize(2);
        assertThat(command.lines().get(0).productType()).isEqualTo(ProductType.INDIVIDUAL);
        assertThat(command.lines().get(0).discountPercentage()).isNull();
        assertThat(command.lines().get(1).discountPercentage()).isEqualByComparingTo("10.00");
        assertThat(command.lines().get(1).species().get(0).speciesKey()).isEqualTo("Carnation");
        assertThat(command.lines().get(1).species().get(0).quantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("an unsellable flower is 409 with the Spanish reason the agent relays")
    void mapsUnavailableSpeciesToConflict() throws Exception {
        when(requestQuotation.requestQuotation(any()))
                .thenThrow(new UnavailableSpeciesException("monkshood", "No disponible"));

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.speciesKey").value("monkshood"))
                .andExpect(jsonPath("$.reason").value("No disponible"));
    }

    @Test
    @DisplayName("a key that is not in the catalog is 400, not 409")
    void mapsUnknownSpeciesToBadRequest() throws Exception {
        when(requestQuotation.requestQuotation(any()))
                .thenThrow(new UnknownSpeciesException("not_a_flower"));

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.speciesKey").value("not_a_flower"));
    }

    @Test
    @DisplayName("a body the command rejects is 400 and never reaches the use case")
    void rejectsAnInvalidBody() throws Exception {
        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionKey\": \"demo-1\", \"lines\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A quotation needs at least one line"));

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionKey": "", "lines": [{"productType": "INDIVIDUAL",
                                 "species": [{"speciesKey": "rose", "quantity": 1}]}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionKey": "demo-1", "lines": [{"productType": "INDIVIDUAL",
                                 "species": [{"speciesKey": "rose", "quantity": 0}]}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(requestQuotation, never()).requestQuotation(any());
    }

    @Test
    @DisplayName("the endpoint is public and CSRF exempt, like the rest of /api")
    void staysPublic() throws Exception {
        when(requestQuotation.requestQuotation(any())).thenReturn(sampleQuotation());

        mockMvc.perform(post("/api/chat/quotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }
}
