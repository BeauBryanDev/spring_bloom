package com.springbloom.adapter.out.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an extracted document into chunks. Static and stateless, so it needs
 * no bean and its test runs with no Spring context.
 *
 * The strategy differs per source on purpose. The policy manual is already
 * written in self-contained numbered sections, and a policy answer should
 * retrieve a whole policy rather than half of one; the market reports and the
 * book are continuous prose with no natural short unit, so they get windows.
 */
public final class KnowledgeChunker {

    /** Roughly 700 tokens of Spanish prose. */
    private static final int WINDOW_WORDS = 500;

    /** One paragraph of overlap, so a fact split across a boundary survives. */
    private static final int OVERLAP_WORDS = 60;

    private static final Pattern SECTION = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
    private static final Pattern FAQ_ENTRY = Pattern.compile("(?=\\b\\d{1,2}\\. ¿)");
    private static final Pattern PARAGRAPH = Pattern.compile("\\n\\s*\\n");

    private KnowledgeChunker() {
    }

    public static List<KnowledgeChunk> chunk(KnowledgeSource source, String text) {
        return switch (source) {
            case SPRING_BLOOM_POLICY -> bySection(source, text);
            case BOTANY_FIGURES -> byParagraph(source, text);
            case COLOMBIA_MARKET, WORLD_MARKET, BOTANY_BOOK -> byWindow(source, text, "");
        };
    }

    /**
     * Splits the manual on its numbered headings. The FAQ section is split once
     * more, per question: 20 unrelated answers in one chunk would retrieve as
     * mostly noise whichever one was asked for.
     */
    static List<KnowledgeChunk> bySection(KnowledgeSource source, String text) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        Matcher matcher = SECTION.matcher(text);

        List<int[]> bounds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            bounds.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(1).trim());
        }
        if (bounds.isEmpty()) {
            return byWindow(source, text, "");
        }

        for (int i = 0; i < bounds.size(); i++) {
            int bodyStart = bounds.get(i)[1];
            int bodyEnd = (i + 1 < bounds.size()) ? bounds.get(i + 1)[0] : text.length();
            String title = titles.get(i);
            String body = text.substring(bodyStart, bodyEnd).trim();
            if (body.isEmpty()) {
                continue;
            }

            if (isFaq(title)) {
                chunks.addAll(splitFaq(source, title, body));
            } else if (wordCount(body) > WINDOW_WORDS * 2) {
                chunks.addAll(byWindow(source, body, title));
            } else {
                chunks.add(KnowledgeChunk.prose(source, title, title + "\n\n" + body));
            }
        }
        return chunks;
    }

    private static boolean isFaq(String title) {
        return title.toLowerCase().contains("preguntas frecuentes");
    }

    private static List<KnowledgeChunk> splitFaq(KnowledgeSource source, String title, String body) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (String entry : FAQ_ENTRY.split(body)) {
            String trimmed = entry.trim();
            if (trimmed.length() < 20) {
                continue;
            }
            chunks.add(new KnowledgeChunk(trimmed, source, title, ChunkKind.FAQ));
        }
        return chunks;
    }

    /** One chunk per paragraph, for the figure legends. */
    static List<KnowledgeChunk> byParagraph(KnowledgeSource source, String text) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (String paragraph : PARAGRAPH.split(text)) {
            String trimmed = paragraph.trim();
            if (trimmed.length() < 20) {
                continue;
            }
            chunks.add(new KnowledgeChunk(trimmed, source, "", ChunkKind.FIGURE));
        }
        return chunks;
    }

    /**
     * Fixed-size windows that break on paragraph boundaries, never mid-sentence,
     * with a paragraph of overlap between neighbours.
     */
    static List<KnowledgeChunk> byWindow(KnowledgeSource source, String text, String section) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : PARAGRAPH.split(text)) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (wordCount(trimmed) > WINDOW_WORDS) {
                paragraphs.addAll(splitSentences(trimmed));
            } else {
                paragraphs.add(trimmed);
            }
        }

        List<String> window = new ArrayList<>();
        int words = 0;
        for (String paragraph : paragraphs) {
            int paragraphWords = wordCount(paragraph);

            if (words > 0 && words + paragraphWords > WINDOW_WORDS) {
                chunks.add(KnowledgeChunk.prose(source, section, String.join("\n\n", window)));
                List<String> carried = overlap(window);
                window = new ArrayList<>(carried);
                words = carried.stream().mapToInt(KnowledgeChunker::wordCount).sum();
            }

            window.add(paragraph);
            words += paragraphWords;
        }
        if (!window.isEmpty()) {
            chunks.add(KnowledgeChunk.prose(source, section, String.join("\n\n", window)));
        }
        return chunks;
    }

    /**
     * A paragraph longer than a whole window is broken at sentence ends. The
     * book's paragraphs run long once unwrapped, and one of them would
     * otherwise be a chunk twice the size of every other.
     */
    private static List<String> splitSentences(String paragraph) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int words = 0;
        for (String sentence : paragraph.split("(?<=[.:;])\\s+")) {
            int sentenceWords = wordCount(sentence);
            if (words > 0 && words + sentenceWords > WINDOW_WORDS) {
                parts.add(current.toString().trim());
                current.setLength(0);
                words = 0;
            }
            current.append(sentence).append(' ');
            words += sentenceWords;
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    /** The trailing paragraphs of a window, up to the overlap budget. */
    private static List<String> overlap(List<String> window) {
        List<String> carried = new ArrayList<>();
        int words = 0;
        for (int i = window.size() - 1; i >= 0; i--) {
            int paragraphWords = wordCount(window.get(i));
            if (words + paragraphWords > OVERLAP_WORDS && !carried.isEmpty()) {
                break;
            }
            carried.add(0, window.get(i));
            words += paragraphWords;
            if (words >= OVERLAP_WORDS) {
                break;
            }
        }
        return carried;
    }

    static int wordCount(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
