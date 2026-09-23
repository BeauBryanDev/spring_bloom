/**
 * The cart buttons and the header badge.
 *
 * The cart itself lives in the customer's HTTP session on the server, not here.
 * This file holds no quantities and no prices - it sends what the customer
 * clicked and renders the count the server sends back. That is the whole point:
 * a price the browser keeps is a price the browser can edit.
 *
 * It used to keep a count in localStorage. That number is gone deliberately: two
 * sources of truth for one cart drift the moment a session expires.
 */
(function () {
    "use strict";

    const API = "/api/cart";

    function badges() {
        return document.querySelectorAll("#cart-count, [data-cart-count]");
    }

    function render(state) {
        if (!state) {
            return;
        }
        badges().forEach(function (badge) {
            badge.textContent = String(state.lines);
        });
    }

    function announce(problem) {
        if (problem) {
            window.alert(problem);
        }
    }

    function call(method, path, body) {
        const options = {
            method: method,
            headers: {"Accept": "application/json"},
            credentials: "same-origin"
        };
        if (body) {
            options.headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }
        return fetch(path, options)
            .then(function (response) {
                return response.json().then(function (state) {
                    return {ok: response.ok, state: state};
                });
            });
    }

    function flash(button) {
        button.classList.add("added");
        window.setTimeout(function () {
            button.classList.remove("added");
        }, 600);
    }

    document.addEventListener("DOMContentLoaded", function () {

        // The badge is read from the server, so a session that expired shows an
        // empty cart rather than a number left over from a previous visit.
        call("GET", API).then(function (result) {
            render(result.state);
        }).catch(function () {
            // Offline or a failed request: leave whatever the page rendered.
        });

        document.addEventListener("click", function (event) {
            const button = event.target.closest("[data-action]");
            if (!button || button.disabled) {
                return;
            }

            const action = button.getAttribute("data-action");
            const key = button.getAttribute("data-species-key");
            let request = null;

            if (action === "add-to-cart" && key) {
                request = call("POST", API + "/items", {speciesKey: key, quantity: 1});
            } else if (action === "cart-increase" && key) {
                request = call("POST", API + "/items", {speciesKey: key, quantity: 1});
            } else if (action === "cart-decrease" && key) {
                request = call("PUT", API + "/items/" + encodeURIComponent(key),
                    {quantity: currentQuantity(button) - 1});
            } else if (action === "cart-remove" && key) {
                request = call("DELETE", API + "/items/" + encodeURIComponent(key));
            } else if (action === "cart-clear") {
                request = call("DELETE", API);
            } else {
                return;
            }

            event.preventDefault();

            request.then(function (result) {
                render(result.state);
                if (!result.ok) {
                    announce(result.state.problem);
                    return;
                }
                if (action === "add-to-cart") {
                    flash(button);
                    return;
                }
                // The cart page shows totals the server computed, so it is
                // reloaded rather than patched line by line in the browser.
                window.location.reload();
            }).catch(function () {
                announce("No pudimos actualizar el carrito. Intente de nuevo.");
            });
        });
    });

    /** The quantity shown beside a +/- pair, read from the page the server rendered. */
    function currentQuantity(button) {
        const row = button.closest(".cart-line-qty");
        const value = row ? row.querySelector(".qty-value") : null;
        const parsed = value ? parseInt(value.textContent, 10) : NaN;
        return isNaN(parsed) ? 1 : parsed;
    }
})();
