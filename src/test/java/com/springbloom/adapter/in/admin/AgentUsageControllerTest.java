package com.springbloom.adapter.in.admin;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springbloom.config.SecurityConfig;
import com.springbloom.domain.model.UsageTotals;
import com.springbloom.domain.port.in.AgentUsageQueryUseCase;
import com.springbloom.domain.port.in.AgentUsageQueryUseCase.Period;
import com.springbloom.domain.port.in.AgentUsageQueryUseCase.Summary;
import com.springbloom.domain.port.out.StoreOwnerRepository;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentUsageController.class)
@Import(SecurityConfig.class)
class AgentUsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentUsageQueryUseCase usage;

    @MockitoBean
    private StoreOwnerRepository owners;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("an admin sees the periods and the estimate")
    void adminSeesPage() throws Exception {
        when(usage.summarize()).thenReturn(new Summary(
                List.of(new Period("Hoy", new UsageTotals(2, 1500, 400, 0, 0), new java.math.BigDecimal("0.0035"))),
                List.of(), 0));

        mockMvc.perform(get("/admin/agent"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hoy")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Costo estimado")));
    }

    @Test
    @DisplayName("anonymous is sent to the login page")
    void anonymousRedirects() throws Exception {
        mockMvc.perform(get("/admin/agent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
    }
}
