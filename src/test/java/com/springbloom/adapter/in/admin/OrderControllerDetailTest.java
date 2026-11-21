package com.springbloom.adapter.in.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springbloom.config.SecurityConfig;
import com.springbloom.domain.model.AdminOrderDetail;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.AdminOrderDetailUseCase;
import com.springbloom.domain.port.in.ListOrdersUseCase;
import com.springbloom.domain.port.in.UpdateShipmentUseCase;
import com.springbloom.domain.port.out.StoreOwnerRepository;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerDetailTest {

    private static final String ORDER = "ORD-20260801-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOrderDetailUseCase detail;

    @MockitoBean
    private ListOrdersUseCase list;

    @MockitoBean
    private UpdateShipmentUseCase shipments;

    @MockitoBean
    private StoreOwnerRepository owners;

    private AdminOrderDetail sample() {
        Instant now = Instant.parse("2026-08-26T15:00:00Z");
        OrderItem item = new OrderItem(ProductType.INDIVIDUAL, null,
                List.of(new OrderItemSpecies(1L, "Rosa", 3, Money.of("4000"), Money.of("12000"))),
                Money.of("12000"));
        FlowerOrder order = new FlowerOrder(10L, ORDER, 5L, 7L, OrderStatus.CONFIRMED, List.of(item),
                Money.of("12000"), Money.of("0"), Money.of("12000"), now);
        return new AdminOrderDetail(order,
                new Customer(7L, "Ana Perez", null, "3001234567", "Calle 1 # 2-3"),
                Optional.empty(), List.of());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("an admin sees the order's lines and customer")
    void adminSeesDetail() throws Exception {
        when(detail.findByNumber(ORDER)).thenReturn(Optional.of(sample()));

        mockMvc.perform(get("/admin/orders/" + ORDER))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Perez")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rosa")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("an unknown number is a 404")
    void unknownIs404() throws Exception {
        when(detail.findByNumber("ORD-NOPE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/orders/ORD-NOPE")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anonymous is sent to the login page")
    void anonymousRedirects() throws Exception {
        mockMvc.perform(get("/admin/orders/" + ORDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
    }
}
