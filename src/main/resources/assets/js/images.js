/**
 * Flower and creation photography is not in the repo yet. Until it is, a card
 * with a missing file shows a tinted placeholder rather than a browser's broken
 * image glyph. The listener is on the capture phase because "error" from an
 * <img> does not bubble.
 */
(function () {
    "use strict";

    document.addEventListener("error", function (event) {
        const image = event.target;
        if (!image || image.tagName !== "IMG") {
            return;
        }
        const frame = image.closest(".product-image, .creation-image");
        if (frame) {
            frame.classList.add("missing");
            image.remove();
        }
    }, true);
})();
