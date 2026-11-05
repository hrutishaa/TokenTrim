package io.tokentrim.compress;

/**
 * Collapses redundant whitespace outside fenced code blocks. Code fences are preserved
 * byte-for-byte aside from newline normalization, so indentation inside them stays intact.
 */
public final class WhitespaceNormalizer {

    public String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        String text = input.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        boolean inFence = false;
        boolean lastWasBlank = false;
        boolean any = false;

        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) {
                if (any) {
                    out.append('\n');
                }
                out.append(line.stripTrailing());
                inFence = !inFence;
                lastWasBlank = false;
                any = true;
                continue;
            }
            if (inFence) {
                if (any) {
                    out.append('\n');
                }
                out.append(line);
                lastWasBlank = false;
                any = true;
                continue;
            }
            String collapsed = collapseSpaces(line.strip());
            if (collapsed.isEmpty()) {
                if (any && !lastWasBlank) {
                    out.append('\n');
                    lastWasBlank = true;
                }
                continue;
            }
            if (any) {
                out.append('\n');
            }
            out.append(collapsed);
            lastWasBlank = false;
            any = true;
        }

        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '\n') {
            end--;
        }
        return out.substring(0, end);
    }

    private static String collapseSpaces(String line) {
        StringBuilder builder = new StringBuilder(line.length());
        boolean pendingSpace = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!pendingSpace && !builder.isEmpty()) {
                    builder.append(' ');
                    pendingSpace = true;
                }
            } else {
                pendingSpace = false;
                builder.append(c);
            }
        }
        return builder.toString().strip();
    }
}
