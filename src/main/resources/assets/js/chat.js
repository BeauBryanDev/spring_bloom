/**
 * Florabelle chat panel. Opening it switches the .page-shell grid to a 75/25
 * split (see main.css); it never takes full width.
 *
 * The conversation survives navigation. Nothing about it lives in this page:
 * the turns are the database's, replayed from GET /api/chat/history for the
 * stored session key, and only the panel's open state and the unsent draft are
 * kept in the browser. A hard refresh, a new tab or a walk through the site all
 * rebuild the same chat.
 */
(function () {
    "use strict";

    const ENDPOINT = "/api/chat/messages";
    const HISTORY_ENDPOINT = "/api/chat/history";
    const SESSION_STORAGE_KEY = "springbloom.sessionKey";
    const OPEN_STORAGE_KEY = "springbloom.chatOpen";
    const DRAFT_STORAGE_KEY = "springbloom.chatDraft";
    const MAX_MESSAGE_LENGTH = 2000;
    const HISTORY_LIMIT = 40;

    // Kept in step with ImageAttachment on the server, which is the one that
    // actually enforces them. Checking here only saves a round trip.
    const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    // What ChatService stores in place of a photo, so a rebuilt history can
    // show that a turn carried one without the image ever being in the column.
    const PHOTO_MARKER = "[foto]";

    function readStored(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (e) {
            // A private window refuses storage; the chat still works for this page.
            return null;
        }
    }

    function writeStored(key, value) {
        try {
            if (value === null) {
                window.localStorage.removeItem(key);
            } else {
                window.localStorage.setItem(key, value);
            }
        } catch (e) {
            // Same: storage is a convenience here, never the source of truth.
        }
    }

    function newSessionKey() {
        return "web-" + (window.crypto && window.crypto.randomUUID
            ? window.crypto.randomUUID()
            : Date.now() + "-" + Math.random().toString(16).slice(2));
    }

    /**
     * The session key is the customer's only identity - the chat has no login,
     * and the backend resolves it to a conversation. Losing it starts a new
     * chat, so it is kept in localStorage rather than in a variable.
     */
    function sessionKey() {
        let key = readStored(SESSION_STORAGE_KEY);
        if (!key) {
            key = newSessionKey();
            writeStored(SESSION_STORAGE_KEY, key);
        }
        return key;
    }

    /** Null until the visitor has actually said something: a page load is not a turn. */
    function existingSessionKey() {
        return readStored(SESSION_STORAGE_KEY);
    }

    function timestamp(value) {
        const at = value ? new Date(value) : new Date();
        if (isNaN(at.getTime())) {
            return "";
        }
        return at.toLocaleTimeString("es-CO", {
            hour: "numeric",
            minute: "2-digit"
        });
    }

    /**
     * Haiku answers in Markdown - bold prices and bulleted options, as in the
     * mockup. Only **bold** and "- " bullets are honoured, and every node is
     * built from text rather than innerHTML, so nothing the model emits can
     * inject markup.
     */
    function renderBold(target, line) {
        const parts = line.split(/\*\*(.+?)\*\*/g);
        parts.forEach(function (part, index) {
            if (part === "") {
                return;
            }
            if (index % 2 === 1) {
                const strong = document.createElement("strong");
                strong.textContent = part;
                target.appendChild(strong);
            } else {
                target.appendChild(document.createTextNode(part));
            }
        });
    }

    function renderMarkdown(target, text) {
        let list = null;

        text.split("\n").forEach(function (line) {
            const bullet = line.match(/^\s*[-*]\s+(.*)$/);

            if (bullet) {
                if (!list) {
                    list = document.createElement("ul");
                    list.className = "chat-list";
                    target.appendChild(list);
                }
                const item = document.createElement("li");
                renderBold(item, bullet[1]);
                list.appendChild(item);
                return;
            }

            list = null;
            if (line.trim() === "") {
                return;
            }
            const paragraph = document.createElement("p");
            renderBold(paragraph, line);
            target.appendChild(paragraph);
        });
    }

    function humanSize(bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024) + " KB";
        }
        return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    }

    /**
     * The first image among dropped or pasted items. A drop can carry several
     * files and a paste usually carries a text flavour alongside the image, so
     * neither can be read as "the file".
     */
    function firstImage(fileList) {
        const files = Array.prototype.slice.call(fileList || []);
        for (let i = 0; i < files.length; i += 1) {
            if (files[i] && files[i].type && files[i].type.indexOf("image/") === 0) {
                return files[i];
            }
        }
        return null;
    }

    function appendRow(thread, role, text, at, thumbnail) {
        const row = document.createElement("div");
        row.className = "chat-row " + role;

        if (role === "agent") {
            const avatar = document.createElement("img");
            avatar.className = "chat-avatar";
            avatar.src = "/assets/icons/florabelle.svg";
            avatar.alt = "";
            row.appendChild(avatar);
        }

        const bubble = document.createElement("div");
        bubble.className = "chat-bubble " + role;

        const body = document.createElement("div");
        body.className = "chat-text";
        if (role === "agent") {
            renderMarkdown(body, text);
        } else {
            // A turn that carried a photo is stored with a marker in front of
            // the words. The image itself was never stored, so a replayed turn
            // shows a note rather than a thumbnail it cannot have.
            let words = text;
            if (words.indexOf(PHOTO_MARKER) === 0) {
                words = words.slice(PHOTO_MARKER.length).trim();
                const note = document.createElement("span");
                note.className = "chat-photo-note";
                note.textContent = "Foto enviada";
                body.appendChild(note);
            }
            if (thumbnail) {
                const preview = document.createElement("img");
                preview.className = "chat-sent-photo";
                preview.src = thumbnail;
                preview.alt = "Foto enviada";
                body.appendChild(preview);
            }
            if (words !== "") {
                const paragraph = document.createElement("p");
                paragraph.textContent = words;
                body.appendChild(paragraph);
            }
        }
        bubble.appendChild(body);

        const time = document.createElement("span");
        time.className = "chat-time";
        time.textContent = timestamp(at);
        bubble.appendChild(time);

        row.appendChild(bubble);
        thread.appendChild(row);
        thread.scrollTop = thread.scrollHeight;
        return bubble;
    }

    /**
     * A quotation number is a real document the database owns, so it is linked
     * rather than left for the customer to copy out of the prose.
     */
    function appendQuotationLink(bubble, quotationNumber) {
        const link = document.createElement("a");
        link.className = "chat-doc-link";
        link.href = "/cotizaciones/" + encodeURIComponent(quotationNumber);
        link.textContent = "Ver Cotizacion " + quotationNumber;
        bubble.insertBefore(link, bubble.querySelector(".chat-time"));
    }

    /**
     * Provenance for a photo turn: which model named the species, and how sure
     * it was. It is built from the response's `vision` object rather than read
     * out of the agent's prose, so what it claims is what the trained network
     * actually returned - the agent cannot write this badge.
     */
    function appendVisionBadge(bubble, vision) {
        const badge = document.createElement("div");
        badge.className = "vision-badge" + (vision.trusted ? "" : " uncertain");

        const title = document.createElement("span");
        title.className = "vision-badge-model";
        title.textContent = vision.model + " · modelo propio";
        badge.appendChild(title);

        const detail = document.createElement("span");
        detail.className = "vision-badge-detail";

        if (!vision.speciesKey) {
            detail.textContent = "Sin deteccion sobre el umbral";
        } else if (vision.trusted) {
            detail.textContent = vision.commonName + " · " + vision.confidencePercent + "% confianza";
        } else {
            detail.textContent = vision.commonName + " · " + vision.confidencePercent
                + "% — bajo el umbral, decide Florabelle";
        }
        badge.appendChild(detail);

        if (vision.scientificName) {
            const latin = document.createElement("span");
            latin.className = "vision-badge-latin";
            latin.textContent = vision.scientificName;
            badge.appendChild(latin);
        }

        bubble.insertBefore(badge, bubble.querySelector(".chat-time"));
    }

    function appendTyping(thread) {
        const row = document.createElement("div");
        row.className = "chat-row agent typing";
        row.innerHTML =
            '<img class="chat-avatar" src="/assets/icons/florabelle.svg" alt=""/>' +
            '<div class="chat-bubble agent"><span class="chat-typing">' +
            "<i></i><i></i><i></i></span></div>";
        thread.appendChild(row);
        thread.scrollTop = thread.scrollHeight;
        return row;
    }

    document.addEventListener("DOMContentLoaded", function () {
        const pageShell = document.querySelector(".page-shell");
        // querySelectorAll, not querySelector: the header carries one of these and
        // a page may carry others (the product page's "Cotizar con Florabelle").
        // Binding only the first left every later button dead.
        const openButtons = document.querySelectorAll("[data-action='open-chat']");
        const closeButton = document.querySelector("[data-action='close-chat']");
        const form = document.getElementById("chat-form");
        const input = document.getElementById("chat-text-input");
        const sendButton = document.getElementById("chat-send-btn");
        const thread = document.getElementById("chat-messages");
        const errorBox = document.getElementById("chat-error");
        const panel = document.querySelector(".chat-panel");
        const dropzone = document.getElementById("chat-dropzone");
        const fileInput = document.getElementById("chat-file-input");
        const attachButton = document.getElementById("chat-attach-btn");
        const attachmentBox = document.getElementById("chat-attachment");
        const attachmentThumb = document.getElementById("chat-attachment-thumb");
        const attachmentName = document.getElementById("chat-attachment-name");
        const attachmentSize = document.getElementById("chat-attachment-size");
        const attachmentRemove = document.getElementById("chat-attachment-remove");

        if (!pageShell) {
            return;
        }

        // Restored before anything is fetched, so a customer who was mid-chat
        // does not watch the panel pop open a beat after the page paints.
        if (readStored(OPEN_STORAGE_KEY) === "true") {
            pageShell.classList.add("chat-open");
        }

        openButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                pageShell.classList.add("chat-open");
                writeStored(OPEN_STORAGE_KEY, "true");
                if (input) {
                    input.focus();
                    // A button that names a flower prefills the question, so the
                    // customer does not retype what they were already looking at.
                    // data-message is a whole prefilled question (the cart's
                    // "Cotizar con Florabelle"); data-flower is just a name.
                    const message = button.getAttribute("data-message");
                    const flower = button.getAttribute("data-flower");
                    if (message && !input.value) {
                        input.value = message;
                        input.dispatchEvent(new Event("input"));
                    } else if (flower && !input.value) {
                        input.value = "Hola, me interesa " + flower + ". ";
                        input.dispatchEvent(new Event("input"));
                    }
                }
            });
        });

        if (closeButton) {
            closeButton.addEventListener("click", function () {
                pageShell.classList.remove("chat-open");
                writeStored(OPEN_STORAGE_KEY, "false");
            });
        }

        if (!form || !input || !thread) {
            return;
        }

        let pending = false;

        // The draft is the one thing the server cannot restore: it was never sent.
        const draft = readStored(DRAFT_STORAGE_KEY);
        if (draft) {
            input.value = draft;
        }
        input.addEventListener("input", function () {
            writeStored(DRAFT_STORAGE_KEY, input.value === "" ? null : input.value);
        });

        /**
         * The panel is rebuilt from the database, not from anything this page
         * kept: the turns that exist are the ones that were persisted. The
         * greeting is markup, so it only survives an empty history.
         */
        function restoreHistory() {
            const key = existingSessionKey();
            if (!key) {
                return;
            }

            fetch(HISTORY_ENDPOINT + "?sessionKey=" + encodeURIComponent(key)
                    + "&limit=" + HISTORY_LIMIT)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("HTTP " + response.status);
                    }
                    return response.json();
                })
                .then(function (history) {
                    const messages = history.messages || [];
                    if (messages.length === 0) {
                        return;
                    }
                    const greeting = thread.querySelector("[data-greeting]");
                    if (greeting) {
                        greeting.remove();
                    }
                    messages.forEach(function (message) {
                        appendRow(
                            thread,
                            message.role === "USER" ? "user" : "agent",
                            message.content,
                            message.createdAt);
                    });
                    thread.scrollTop = thread.scrollHeight;
                })
                .catch(function () {
                    // An unreachable history is not a broken chat: the customer
                    // can still send a turn, and the server still has the rest.
                    showError("No pudimos recuperar la conversacion anterior.");
                });
        }

        function showError(message) {
            if (!errorBox) {
                return;
            }
            errorBox.textContent = message;
            errorBox.hidden = false;
        }

        function clearError() {
            if (errorBox) {
                errorBox.hidden = true;
            }
        }

        function setPending(value) {
            pending = value;
            input.disabled = value;
            if (sendButton) {
                sendButton.disabled = value;
            }
            if (attachButton) {
                attachButton.disabled = value;
            }
        }

        /**
         * The one photo waiting to be sent: a data URL and the file's name.
         * Never more than one - a turn is a question about a flower, and the
         * vision model reads one flower per image anyway.
         */
        let attachment = null;

        function clearAttachment() {
            attachment = null;
            if (fileInput) {
                // Cleared so re-picking the same file still fires change.
                fileInput.value = "";
            }
            if (attachmentBox) {
                attachmentBox.hidden = true;
                attachmentThumb.removeAttribute("src");
                attachmentName.textContent = "";
                attachmentSize.textContent = "";
            }
        }

        function showAttachment(file, dataUrl) {
            attachment = { dataUrl: dataUrl, name: file.name };
            if (!attachmentBox) {
                return;
            }
            attachmentThumb.src = dataUrl;
            attachmentName.textContent = file.name;
            attachmentSize.textContent = humanSize(file.size);
            attachmentBox.hidden = false;
        }

        /**
         * Reads the file to a data URL, which is exactly the shape the server's
         * ChatRequest.attachment() accepts. The type check is a courtesy: the
         * server sniffs the bytes and would reject a mislabelled file anyway.
         */
        function acceptFile(file) {
            if (!file) {
                return;
            }
            if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                showError("Solo podemos leer fotos JPG, PNG o WebP.");
                return;
            }
            if (file.size > MAX_IMAGE_BYTES) {
                showError("Esa foto pesa mas de 10MB. Intenta con una mas liviana.");
                return;
            }

            const reader = new FileReader();
            reader.onload = function () {
                clearError();
                showAttachment(file, String(reader.result));
                input.focus();
            };
            reader.onerror = function () {
                showError("No pudimos leer esa foto. Intenta con otra.");
            };
            reader.readAsDataURL(file);
        }

        if (attachButton && fileInput) {
            attachButton.addEventListener("click", function () {
                fileInput.click();
            });
            fileInput.addEventListener("change", function () {
                acceptFile(firstImage(fileInput.files));
            });
        }

        if (attachmentRemove) {
            attachmentRemove.addEventListener("click", function () {
                clearAttachment();
                input.focus();
            });
        }

        /**
         * Drag and drop over the whole panel.
         *
         * dragenter/dragover must both preventDefault or the browser keeps the
         * drop for itself and navigates away to the image - the single most
         * common way this feature silently fails. The counter exists because
         * dragleave fires on every child element the pointer crosses, so a bare
         * listener would flicker the overlay off while the file is still over
         * the panel.
         */
        if (panel && dropzone) {
            let dragDepth = 0;

            const showDropzone = function (visible) {
                dropzone.hidden = !visible;
                panel.classList.toggle("dragging", visible);
            };

            panel.addEventListener("dragenter", function (event) {
                event.preventDefault();
                dragDepth += 1;
                showDropzone(true);
            });

            panel.addEventListener("dragover", function (event) {
                event.preventDefault();
                event.dataTransfer.dropEffect = "copy";
            });

            panel.addEventListener("dragleave", function () {
                dragDepth = Math.max(0, dragDepth - 1);
                if (dragDepth === 0) {
                    showDropzone(false);
                }
            });

            panel.addEventListener("drop", function (event) {
                event.preventDefault();
                dragDepth = 0;
                showDropzone(false);
                if (pending) {
                    return;
                }
                acceptFile(firstImage(event.dataTransfer && event.dataTransfer.files));
            });
        }

        // A dropped file outside the panel would navigate the tab away from the
        // site, which mid-conversation reads as the page crashing.
        window.addEventListener("dragover", function (event) {
            event.preventDefault();
        });
        window.addEventListener("drop", function (event) {
            event.preventDefault();
        });

        // Pasting a screenshot is the same gesture by another route.
        input.addEventListener("paste", function (event) {
            const items = event.clipboardData && event.clipboardData.files;
            const image = firstImage(items);
            if (image) {
                event.preventDefault();
                acceptFile(image);
            }
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();

            const message = input.value.trim();

            // A photo on its own is a complete turn: dropping a flower on
            // Florabelle is the question. Words alone still are too.
            if (pending || message.length > MAX_MESSAGE_LENGTH) {
                return;
            }
            if (message === "" && !attachment) {
                return;
            }

            const sent = attachment;
            clearError();
            appendRow(thread, "user", message, null, sent ? sent.dataUrl : null);
            input.value = "";
            clearAttachment();
            writeStored(DRAFT_STORAGE_KEY, null);
            setPending(true);
            const typingRow = appendTyping(thread);

            fetch(ENDPOINT, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    sessionKey: sessionKey(),
                    message: message,
                    image: sent ? sent.dataUrl : null
                })
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("HTTP " + response.status);
                    }
                    return response.json();
                })
                .then(function (turn) {
                    typingRow.remove();
                    const bubble = appendRow(thread, "agent", turn.reply);
                    if (turn.vision) {
                        appendVisionBadge(bubble, turn.vision);
                    }
                    if (turn.quotationNumber) {
                        appendQuotationLink(bubble, turn.quotationNumber);
                    }
                })
                .catch(function () {
                    typingRow.remove();
                    showError("No pudimos enviar tu mensaje. Intenta de nuevo.");
                })
                .then(function () {
                    setPending(false);
                    input.focus();
                });
        });

        restoreHistory();
    });
})();
