package com.springbloom.adapter.in.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.springbloom.domain.port.in.ListOrdersUseCase;

import lombok.RequiredArgsConstructor;

/**
 * Read-only order listing. The write side - ConfirmOrderUseCase, OrderItem,
 * stock decrement on confirmation ...
 */
@Controller
@RequiredArgsConstructor
public class OrderController {

    private final ListOrdersUseCase listOrdersUseCase;

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", listOrdersUseCase.listAll());
        return "admin/orders";
    }
}
