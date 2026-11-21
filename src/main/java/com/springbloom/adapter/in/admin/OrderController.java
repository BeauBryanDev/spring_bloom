package com.springbloom.adapter.in.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.springbloom.domain.port.in.AdminOrderDetailUseCase;
import com.springbloom.domain.port.in.ListOrdersUseCase;
import com.springbloom.domain.port.in.UpdateShipmentUseCase;

import lombok.RequiredArgsConstructor;

/**
 * The order list plus the two real-world events: dispatched and delivered.
 * Behind the /admin/** rule in SecurityConfig, so only a logged-in owner reaches it.
 */
@Controller
@RequiredArgsConstructor
public class OrderController {

    private final ListOrdersUseCase listOrdersUseCase;
    private final UpdateShipmentUseCase updateShipmentUseCase;
    private final AdminOrderDetailUseCase adminOrderDetailUseCase;

    @GetMapping("/admin/orders")
    public String orders(Model model) {
        model.addAttribute("orders", listOrdersUseCase.listAll());
        return "admin/orders";
    }

    @GetMapping("/admin/orders/{orderNumber}")
    public String detail(@PathVariable String orderNumber, Model model) {
        var detail = adminOrderDetailUseCase.findByNumber(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("detail", detail);
        return "admin/order-detail";
    }

    @PostMapping("/admin/orders/{orderNumber}/dispatch")
    public String dispatch(@PathVariable String orderNumber,
            @RequestParam(required = false) String from, RedirectAttributes flash) {
        try {
            updateShipmentUseCase.dispatch(orderNumber);
            flash.addFlashAttribute("done", "Pedido " + orderNumber + " despachado");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("problem", refused.getMessage());
        }
        return back(orderNumber, from);
    }

    @PostMapping("/admin/orders/{orderNumber}/deliver")
    public String deliver(@PathVariable String orderNumber,
            @RequestParam(required = false) String from, RedirectAttributes flash) {
        try {
            updateShipmentUseCase.deliver(orderNumber);
            flash.addFlashAttribute("done", "Pedido " + orderNumber + " marcado como entregado");
        } catch (IllegalArgumentException refused) {
            flash.addFlashAttribute("problem", refused.getMessage());
        }
        return back(orderNumber, from);
    }

    private static String back(String orderNumber, String from) {
        return "detail".equals(from) ? "redirect:/admin/orders/" + orderNumber : "redirect:/admin/orders";
    }
}
