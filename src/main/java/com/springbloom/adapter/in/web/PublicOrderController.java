package com.springbloom.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springbloom.domain.port.in.PayOrderUseCase;
import com.springbloom.domain.port.in.PayOrderUseCase.OrderView;
import com.springbloom.domain.port.in.PayOrderUseCase.ViewLine;
import com.springbloom.domain.service.PaymentNotConfiguredException;

import lombok.RequiredArgsConstructor;

/**
 * The cart page's "I already have an order" box. Under /api/** so the CSRF
 * exemption already covers it. The body never carries the customer's name,
 * phone or address: an order number is not a secret.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final PayOrderUseCase payOrder;

    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> lookup(@PathVariable String orderNumber) {
        return payOrder.view(orderNumber)
                .<ResponseEntity<?>>map(view -> ResponseEntity.ok(OrderBody.of(view)))
                .orElseGet(() -> problem(HttpStatus.NOT_FOUND, "No encontramos ese pedido"));
    }

    @PostMapping("/{orderNumber}/checkout")
    public ResponseEntity<?> checkout(@PathVariable String orderNumber) {
        try {
            return ResponseEntity.ok(Map.of("url", payOrder.startCheckout(orderNumber).url()));
        } catch (IllegalArgumentException unknown) {
            return problem(HttpStatus.NOT_FOUND, unknown.getMessage());
        } catch (IllegalStateException notPayable) {
            return problem(HttpStatus.CONFLICT, notPayable.getMessage());
        } catch (PaymentNotConfiguredException unavailable) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage());
        }
    }

    private static ResponseEntity<Map<String, String>> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    /** Money leaves as strings, never JSON numbers. */
    public record OrderBody(
            String orderNumber,
            String status,
            boolean paid,
            boolean payable,
            List<LineBody> lines,
            String subtotal,
            String discountAmount,
            String total) {

        static OrderBody of(OrderView view) {
            return new OrderBody(
                    view.orderNumber(),
                    view.status().name(),
                    view.paid(),
                    view.payable(),
                    view.lines().stream().map(LineBody::of).toList(),
                    view.subtotal().amount().toPlainString(),
                    view.discountAmount().amount().toPlainString(),
                    view.total().amount().toPlainString());
        }
    }

    public record LineBody(String productType, String name, int quantity, String unitPrice, String lineTotal) {

        static LineBody of(ViewLine line) {
            return new LineBody(line.productType().name(), line.name(), line.quantity(),
                    line.unitPrice().amount().toPlainString(), line.lineTotal().amount().toPlainString());
        }
    }
}
