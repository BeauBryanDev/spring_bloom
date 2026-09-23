package com.springbloom.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure: the cart is plain Java with no Spring and no persistence. */
class CartTest {

    @Test
    @DisplayName("adding the same flower twice adds the stems together")
    void addingTwiceAccumulates() {
        Cart cart = new Cart();
        cart.add("rose", 5);
        cart.add("rose", 7);

        assertThat(cart.lineCount()).isEqualTo(1);
        assertThat(cart.totalStems()).isEqualTo(12);
    }

    @Test
    @DisplayName("lines keep the order they were added, so the page does not reshuffle")
    void insertionOrderIsKept() {
        Cart cart = new Cart();
        cart.add("rose", 1);
        cart.add("Carnation", 1);
        cart.add("peruvian_lily", 1);
        cart.add("rose", 1);

        assertThat(cart.lines())
                .extracting(Cart.CartLine::speciesKey)
                .containsExactly("rose", "Carnation", "peruvian_lily");
    }

    @Test
    @DisplayName("setting a quantity of zero removes the line, which is what a customer means")
    void zeroQuantityRemoves() {
        Cart cart = new Cart();
        cart.add("rose", 5);
        cart.setQuantity("rose", 0);

        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("the cart never holds a price, only keys and quantities")
    void holdsNoMoney() {
        Cart cart = new Cart();
        cart.add("rose", 5);

        List<Cart.CartLine> lines = cart.lines();
        assertThat(lines).hasSize(1);
        assertThat(Cart.CartLine.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("speciesKey", "quantity");
    }

    @Test
    @DisplayName("lines() is a copy: the page cannot edit the cart by holding its list")
    void linesAreImmutable() {
        Cart cart = new Cart();
        cart.add("rose", 1);

        assertThatThrownBy(() -> cart.lines().add(new Cart.CartLine("Carnation", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a quantity is capped rather than allowed to grow without limit")
    void quantityIsCapped() {
        Cart cart = new Cart();
        cart.add("rose", Cart.MAX_QUANTITY_PER_LINE);
        cart.add("rose", 50);

        assertThat(cart.totalStems()).isEqualTo(Cart.MAX_QUANTITY_PER_LINE);
    }

    @Test
    @DisplayName("a full cart refuses a new flower but still accepts more of what it holds")
    void lineLimitIsEnforced() {
        Cart cart = new Cart();
        for (int i = 0; i < Cart.MAX_LINES; i++) {
            cart.add("species-" + i, 1);
        }

        assertThatThrownBy(() -> cart.add("one-too-many", 1))
                .isInstanceOf(IllegalStateException.class);

        cart.add("species-0", 1);
        assertThat(cart.lineCount()).isEqualTo(Cart.MAX_LINES);
    }

    @Test
    @DisplayName("a blank key or a non-positive quantity is rejected")
    void badInputRejected() {
        Cart cart = new Cart();

        assertThatThrownBy(() -> cart.add("  ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add("rose", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.add("rose", -3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("removing and clearing empty the cart")
    void removeAndClear() {
        Cart cart = new Cart();
        cart.add("rose", 5);
        cart.add("Carnation", 5);

        cart.remove("rose");
        assertThat(cart.lineCount()).isEqualTo(1);

        cart.clear();
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.totalStems()).isZero();
    }

    @Test
    @DisplayName("species_key matching stays exact and case sensitive")
    void keysAreCaseSensitive() {
        Cart cart = new Cart();
        cart.add("Carnation", 1);
        cart.add("carnation", 1);

        assertThat(cart.lineCount()).isEqualTo(2);
    }
}
