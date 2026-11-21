package com.springbloom.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.springbloom.domain.model.OrderStatus.CANCELLED;
import static com.springbloom.domain.model.OrderStatus.CONFIRMED;
import static com.springbloom.domain.model.OrderStatus.DELIVERED;
import static com.springbloom.domain.model.OrderStatus.DISPATCHED;
import static com.springbloom.domain.model.OrderStatus.PENDING;
import static com.springbloom.domain.model.OrderStatus.PREPARING;
import static com.springbloom.domain.model.OrderStatus.READY_FOR_DISPATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> EDGES = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PREPARING, DISPATCHED),
            PREPARING, EnumSet.of(READY_FOR_DISPATCH, DISPATCHED),
            READY_FOR_DISPATCH, EnumSet.of(DISPATCHED),
            DISPATCHED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    @Test
    @DisplayName("every pair of states is legal exactly when the table says so")
    void fullMatrix() {
        assertThat(EDGES).hasSize(OrderStatus.values().length);
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(EDGES.get(from).contains(to));
            }
        }
    }

    @Test
    @DisplayName("DELIVERED and CANCELLED are terminal, nothing else is")
    void terminalStates() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.terminal()).isEqualTo(status == DELIVERED || status == CANCELLED);
        }
    }

    @Test
    @DisplayName("only paid, not-yet-shipped orders can be dispatched; only dispatched ones delivered")
    void dispatchAndDeliver() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canDispatch())
                    .as("canDispatch %s", status)
                    .isEqualTo(status == CONFIRMED || status == PREPARING || status == READY_FOR_DISPATCH);
            assertThat(status.canDeliver()).as("canDeliver %s", status).isEqualTo(status == DISPATCHED);
        }
    }

    @Test
    @DisplayName("an illegal move names both states")
    void requireNamesBothStates() {
        assertThatThrownBy(() -> DELIVERED.requireTransitionTo(PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELIVERED")
                .hasMessageContaining("PENDING");
        PENDING.requireTransitionTo(CONFIRMED);
    }
}
