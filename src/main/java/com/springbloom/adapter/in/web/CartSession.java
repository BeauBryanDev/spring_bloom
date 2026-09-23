package com.springbloom.adapter.in.web;

import java.io.Serializable;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import com.springbloom.domain.model.Cart;

/**
 * Holds one customer's cart in their HTTP session.
 *
 * Deliberately not a table. A cart is scratch state until it becomes an order,
 * the anonymous session key is its only owner, and a cart table written before
 * ConfirmOrderUseCase exists would be a third near-copy of quotation_item plus
 * a pile of abandoned rows nothing cleans up. When the order half lands, this
 * same Cart is what it reads - and that is when persistence gets decided.
 *
 * DO NOT REMOVE proxyMode = TARGET_CLASS.
 *
 * CartController is a singleton and its dependencies are injected once, at
 * startup. Without the proxy, Spring resolves this bean a single time - for
 * whichever request came first - and every visitor from then on shares that one
 * cart, seeing each other's flowers and changing each other's totals.
 *
 * The reason this is a warning and not a footnote: nothing fails when it is
 * wrong. It compiles, it boots, the tests pass, and it behaves perfectly with
 * one browser open, because one browser is one session. The bug only appears
 * once two people use the site at the same time, and it is a cross-customer
 * data leak when it does.  
 */
@Component
@SessionScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CartSession implements Serializable {

    private final Cart cart = new Cart();

    public Cart cart() {
        return cart;
    }
}
