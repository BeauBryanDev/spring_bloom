/**
 * The catalog's two dropdowns. The filtering itself is the server's - the URL
 * carries the choices - so this only saves the customer a click on "Aplicar".
 * The <noscript> button in the form is what happens without it.
 */
(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        const form = document.querySelector("[data-auto-submit]");
        if (!form) {
            return;
        }

        form.addEventListener("change", function (event) {
            if (event.target.tagName === "SELECT") {
                form.submit();
            }
        });
    });
})();
