package com.springbloom.adapter.in.web;

import com.springbloom.adapter.in.web.CartPricing.CartLineView;
import com.springbloom.adapter.in.web.CartPricing.CartView;
import com.springbloom.domain.model.Cart;
import com.springbloom.domain.port.in.CheckoutCartUseCase;
import com.springbloom.domain.port.in.CheckoutCartUseCase.CartCheckout;
import com.springbloom.domain.port.in.CheckoutCartUseCase.CheckoutCartCommand;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;
import com.springbloom.domain.port.in.ListCatalogUseCase;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cart page and the small API behind the cart buttons.
 *
 * The cart lives in the session, not in a table - see CartSession. The API sits
 * under /api/cart because SecurityConfig already exempts /api/** from CSRF,
 * which is what lets a plain fetch() from a card work without a token.
 *
 * Every response reports the whole cart back, so the header badge is refreshed
 * from the server's own count rather than from a number the browser kept.
 */
@Controller
public class CartController {

    private final CartSession session;
    private final CartPricing pricing;
    private final ListCatalogUseCase listCatalog;
    private final CheckoutCartUseCase checkoutCart;

    public CartController(CartSession session, 
                            CartPricing pricing, 
                            ListCatalogUseCase listCatalog,
                            CheckoutCartUseCase checkoutCart) {

        this.session = session;
        this.pricing = pricing;
        this.listCatalog = listCatalog;
        this.checkoutCart = checkoutCart;
    }

    @GetMapping("/carrito")
    public String cart(Model model) {
        CartView view = pricing.price(session.cart());

        model.addAttribute("cart", view);
        model.addAttribute("lines", view.lines().stream().map(CartController::toRow).toList());
        model.addAttribute("subtotal", formatCop(view.subtotal().amount()));
        model.addAttribute("total", formatCop(view.total().amount()));
        model.addAttribute("discountAmount", formatCop(view.discountAmount().amount()));
        model.addAttribute("discountPercentage", trim(view.discountPercentage()));
        model.addAttribute("quotePrompt", quotePrompt(view));
        model.addAttribute("activePage", "carrito");
        return "pages/cart";
    }

    @PostMapping("/api/cart/items")
    @ResponseBody
    public ResponseEntity<CartState> add(@RequestBody CartItemRequest request) {
        return apply(() -> {
            requireSellable(request.speciesKey());
            session.cart().add(request.speciesKey(), request.quantityOrOne());
        });
    }

    /**
     * Nothing enters the cart that the shop cannot sell. The disabled button on
     * a card is a courtesy; this is the check that holds, and it is why an
     * unknown species_key is a 400 rather than a line reading "ya no esta".
     *
     * The cart page still renders unsellable lines, because a flower can go
     * NOT_FOR_SALE while it is sitting in someone's session.
     */
    private void requireSellable(String speciesKey) {
        CatalogEntry entry = listCatalog.findByKey(speciesKey == null ? "" : speciesKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("No conocemos esa flor"));

        if (entry.unitPrice().isEmpty()) {
            throw new IllegalArgumentException(
                    entry.commonName() + " no esta a la venta: " + entry.availabilityLabel());
        }
    }

    @PutMapping("/api/cart/items/{speciesKey}")
    @ResponseBody
    public ResponseEntity<CartState> update(
            @PathVariable String speciesKey, 
            @RequestBody CartItemRequest request) {
        return apply(() -> {
            if (request.quantityOrZero() > 0) {
                requireSellable(speciesKey);
            }
            session.cart().setQuantity(speciesKey, request.quantityOrZero());
        });
    }

    @DeleteMapping("/api/cart/items/{speciesKey}")
    @ResponseBody
    public ResponseEntity<CartState> remove(@PathVariable String speciesKey) {
        return apply(() -> session.cart().remove(speciesKey));
    }

    @DeleteMapping("/api/cart")
    @ResponseBody
    public ResponseEntity<CartState> clear() {
        return apply(() -> session.cart().clear());
    }

    /**
     * Turns the session's cart into a quotation and an order and answers with the
     * Stripe URL. The browser sends only who and where: the flowers come from the
     * session and every price from flower_stock.
     *
     * Locked on the Cart instance, not on the session proxy (which is a singleton
     * shared by every visitor), so a double click cannot place two orders. The cart
     * is emptied once the order exists, even if Stripe then fails: the order can be
     * paid later from "Ya tengo un pedido".
     */
    @PostMapping("/api/cart/checkout")
    @ResponseBody
    public ResponseEntity<Map<String, String>> checkout(@RequestBody CheckoutRequest request) {
        Cart cart = session.cart();
        synchronized (cart) {
            CartView view = pricing.price(cart);
            if (view.isEmpty()) {
                return problem(HttpStatus.BAD_REQUEST, "Su carrito esta vacio");
            }
            if (view.anyUnavailable()) {
                return problem(HttpStatus.CONFLICT,
                        "Quite del carrito las flores que no estan a la venta antes de pagar");
            }
            try {
                CartCheckout placed = checkoutCart.checkout(new CheckoutCartCommand(
                        cart.lines().stream()
                                .map(line -> new CheckoutCartUseCase.CartLine(line.speciesKey(), 
                                                                            line.quantity()))
                                .toList(),
                        request.fullName(), request.phone(), request.deliveryAddress(), request.email()));
                cart.clear();
                return ResponseEntity.ok(placedBody(placed));
            } catch (UnavailableSpeciesException unavailable) {
                return problem(HttpStatus.CONFLICT,
                        nameOf(view, unavailable.getSpeciesKey()) + ": " + unavailable.getReason());
            } catch (UnknownSpeciesException unknown) {
                return problem(HttpStatus.CONFLICT, "Una de las flores ya no esta en el catalogo");
            } catch (IllegalArgumentException invalid) {
                return problem(HttpStatus.BAD_REQUEST, invalid.getMessage());
            }
        }
    }

    private static Map<String, String> placedBody(CartCheckout placed) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("orderNumber", placed.orderNumber());
        body.put("quotationNumber", placed.quotationNumber());
        if (placed.paymentUrl() != null) {
            body.put("url", placed.paymentUrl());
        }
        if (placed.paymentProblem() != null) {
            body.put("message", placed.paymentProblem());
        }
        return body;
    }

    private static String nameOf(CartView view, String speciesKey) {
        return view.lines().stream()
                .filter(line -> line.speciesKey().equals(speciesKey))
                .map(CartLineView::commonName)
                .findFirst()
                .orElse(speciesKey);
    }

    private static ResponseEntity<Map<String, String>> problem(HttpStatus status, String message) {

        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @GetMapping("/api/cart")
    @ResponseBody
    public CartState state() {
        return state(null);
    }

    /**
     * A cart that is too full or a quantity that makes no sense is a 400 with a
     * Spanish reason the page can show, not a stack trace. The cart itself is
     * returned either way so the badge never goes stale after a rejected click.
     */
    private ResponseEntity<CartState> apply(Runnable mutation) {
        try {
            mutation.run();
            return ResponseEntity.ok(state(null));

        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return ResponseEntity.badRequest().body(state(rejected.getMessage()));
        }
    }

    private CartState state(String problem) {
        CartView view = pricing.price(session.cart());

        return new CartState(
                view.lines().size(),
                view.totalStems(),
                formatCop(view.total().amount()),
                problem);
    }

    /**
     * The message the "Cotizar con Florabelle" button drops into the chat. It
     * names the flowers and the stems and asks for a quotation - it never states
     * a price or a discount, because those are hers to look up. Unsellable lines
     * are left out: there is nothing to quote for them.
     */
    private static String quotePrompt(CartView view) {
        List<String> parts = view.lines().stream()
                .filter(CartLineView::quotable)
                .map(line -> line.quantity() + " tallos de " + line.commonName())
                .toList();

        if (parts.isEmpty()) {
            return null;
        }
        return "Hola, quisiera cotizar un ramo con " + String.join(", ", parts)
                + ". Me puede dar el total?";
    }

    private static CartRow toRow(CartLineView line) {
        return new CartRow(
                line.speciesKey(),
                line.commonName(),
                line.scientificName(),
                line.imagePath(),
                line.quantity(),
                line.quotable(),
                line.enoughStock(),
                line.availability(),
                line.availabilityLabel(),
                line.unitPrice() == null ? null : formatCop(line.unitPrice().amount()),
                line.lineTotal() == null ? null : formatCop(line.lineTotal().amount()));
    }

    /** What the cart page renders. Money is already formatted; nothing computes here. */
    public record CartRow(
            String speciesKey,
            String commonName,
            String scientificName,
            String imagePath,
            int quantity,
            boolean quotable,
            boolean enoughStock,
            String availability,
            String availabilityLabel,
            String unitPrice,
            String lineTotal) {
    }

    /** What every /api/cart call answers with. problem is null when nothing failed. */
    public record CartState(int lines, 
        int totalStems, 
        String total, 
        String problem) {
    }

    /** Who the order is for. Never a price or a flower: those are the server's. */
    public record CheckoutRequest(String fullName, 
                                String phone, 
                                String deliveryAddress, 
                                String email) {
    }

    public record CartItemRequest(String speciesKey, Integer quantity) {

        int quantityOrOne() {
            return quantity == null ? 1 : quantity;
        }

        int quantityOrZero() {
            return quantity == null ? 0 : quantity;
        }
    }

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Colombian grouping, whole pesos - the same format the catalog cards use. */
    private static String formatCop(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return "$" + format.format(amount) + " COP";
    }
}
