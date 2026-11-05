package io.tokentrim.compress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes stock assistant filler ("Sure, I'd be happy to help", "I hope this helps")
 * from prose. Text inside markdown code fences is left untouched.
 */
public final class BoilerplateStripper {

    private static final Pattern FENCE = Pattern.compile("(?s)```.*?```");

    private final List<Pattern> patterns;

    public BoilerplateStripper(List<String> phrases) {
        this.patterns = new ArrayList<>();
        if (phrases == null) {
            return;
        }
        for (String phrase : phrases) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            String[] words = phrase.trim().toLowerCase(Locale.ROOT).split("\\s+");
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i > 0) {
                    body.append("\\s+");
                }
                body.append(Pattern.quote(words[i]));
            }
            patterns.add(Pattern.compile("(?i)(^|[\\s\\n])" + body + "[!.,]*"));
        }
    }

    public String strip(String text) {
        if (text == null || text.isEmpty() || patterns.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher fences = FENCE.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int cursor = 0;
        while (fences.find()) {
            out.append(stripPlain(text.substring(cursor, fences.start())));
            out.append(fences.group());
            cursor = fences.end();
        }
        out.append(stripPlain(text.substring(cursor)));
        return out.toString();
    }

    private String stripPlain(String text) {
        String current = text;
        for (Pattern pattern : patterns) {
            current = pattern.matcher(current).replaceAll(" ");
        }
        return current;
    }
}
