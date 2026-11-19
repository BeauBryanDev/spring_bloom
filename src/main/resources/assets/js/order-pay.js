/**
 * "Ya tengo un pedido": look an order up by its number and send the customer to
 * Stripe's hosted checkout.
 *
 * Holds no prices. Every figure comes from GET /api/orders/{number}, and the
 * order is only ever marked paid by the server's signed webhook - landing back
 * here with ?pago=exitoso proves nothing, so the page just polls the order's
 * real status until the webhook has confirmed it.
 */
(function () {
    "use strict";

    const API = "/api/orders/";
    const POLL_MS = 2000;
    const POLL_LIMIT = 15;

    const form = document.getElementById("order-pay-form");
    if (!form) {
        return;
    }

    const input = document.getElementById("order-number");
    const message = document.getElementById("order-pay-message");
    const result = document.getElementById("order-pay-result");
    const title = document.getElementById("order-pay-title");
    const lines = document.getElementById("order-pay-lines");
    const total = document.getElementById("order-pay-total");
    const discount = document.getElementById("order-pay-discount");
    const discountRow = document.getElementById("order-pay-discount-row");
    const payButton = document.getElementById("order-pay-button");

    let current = null;

    function pesos(value) {
        const number = Math.round(parseFloat(value));
        return "$" + number.toLocaleString("es-CO") + " COP";
    }

    function say(text) {
        message.textContent = text || "";
        message.hidden = !text;
    }

    function show(order) {
        current = order;
        title.textContent = "Pedido " + order.orderNumber;

        lines.textContent = "";
        order.lines.forEach(function (line) {
            const item = document.createElement("li");
            item.textContent = line.quantity + " x " + line.name + " - " + pesos(line.lineTotal);
            lines.appendChild(item);
        });

        const saved = parseFloat(order.discountAmount);
        discountRow.hidden = !(saved > 0);
        discount.textContent = "- " + pesos(order.discountAmount);
        total.textContent = pesos(order.total);

        payButton.hidden = !order.payable;
        result.hidden = false;

        if (order.paid && order.status === "CONFIRMED") {
            say("Pago recibido. Su pedido esta confirmado. Gracias por su compra.");
        } else if (order.paid) {
            say("Recibimos su pago. El equipo revisara su pedido y se comunicara con usted.");
        } else if (order.status === "CANCELLED") {
            say("Este pedido fue cancelado.");
        } else if (!order.payable) {
            say("Este pedido ya no admite pago (estado: " + order.status + ").");
        }
    }

    function fetchOrder(number) {
        return fetch(API + encodeURIComponent(number), {headers: {"Accept": "application/json"}})
            .then(function (response) {
                return response.json().then(function (body) {
                    return {ok: response.ok, body: body};
                });
            });
    }

    function lookup(number) {
        say("");
        result.hidden = true;
        return fetchOrder(number).then(function (reply) {
            if (!reply.ok) {
                say(reply.body.message || "No encontramos ese pedido.");
                return null;
            }
            show(reply.body);
            return reply.body;
        }).catch(function () {
            say("No pudimos consultar el pedido. Intente de nuevo.");
            return null;
        });
    }

    /** After checkout: wait for the webhook rather than trusting the redirect. */
    function waitForConfirmation(number, attempt) {
        say("Estamos confirmando su pago...");
        fetchOrder(number).then(function (reply) {
            if (reply.ok) {
                show(reply.body);
                if (reply.body.paid) {
                    return;
                }
            }
            if (attempt >= POLL_LIMIT) {
                say("Aun no vemos la confirmacion del pago. Si ya pago, recargue esta pagina en un momento.");
                return;
            }
            window.setTimeout(function () {
                waitForConfirmation(number, attempt + 1);
            }, POLL_MS);
        }).catch(function () {
            say("No pudimos confirmar el pago ahora. Recargue esta pagina en un momento.");
        });
    }

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        const number = input.value.trim();
        if (number) {
            lookup(number);
        }
    });

    payButton.addEventListener("click", function () {
        if (!current) {
            return;
        }
        payButton.disabled = true;
        fetch(API + encodeURIComponent(current.orderNumber) + "/checkout", {
            method: "POST",
            headers: {"Accept": "application/json"}
        }).then(function (response) {
            return response.json().then(function (body) {
                if (!response.ok || !body.url) {
                    throw new Error(body.message || "No pudimos iniciar el pago.");
                }
                window.location.href = body.url;
            });
        }).catch(function (failure) {
            say(failure.message);
            payButton.disabled = false;
        });
    });

    document.addEventListener("DOMContentLoaded", function () {
        const params = new URLSearchParams(window.location.search);
        const number = params.get("orden");
        if (!number) {
            return;
        }
        input.value = number;

        if (params.get("pago") === "exitoso") {
            waitForConfirmation(number, 0);
        } else {
            lookup(number).then(function () {
                if (params.get("pago") === "cancelado") {
                    say("Cancelo el pago. Su pedido sigue pendiente: puede pagarlo cuando quiera.");
                }
            });
        }
    });
})();
