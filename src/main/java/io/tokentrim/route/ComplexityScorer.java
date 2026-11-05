package io.tokentrim.route;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.embed.MessageTexts;
import io.tokentrim.token.TokenCounter;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic difficulty score in {@code [0, 1]}. Length alone stays under the default
 * "simple" route; reasoning, code, and analysis cues are what promote a prompt to the
 * larger model. The score is intentionally local so routing does not spend an extra model call.
 */
public final class ComplexityScorer {

    private static final Pattern PROOF = Pattern.compile(
            "\\b(prove|derive|theorem|lemma|big-o|time complexity|space complexity)\\b");
    private static final Pattern REASONING = Pattern.compile("step by step|chain of thought|reason carefully");
    private static final Pattern CODE = Pattern.compile(
            "\\b(implement|refactor|debug|algorithm|pseudocode)\\b|```|write a (function|program|class|script)");
    private static final Pattern ANALYSIS = Pattern.compile("\\b(compare|trade-?offs?|analy[sz]e|evaluate)\\b");
    private static final Pattern SYSTEMS = Pattern.compile("\\b(sql query|regex|concurrent|distributed system)\\b");

    private final TokenCounter tokenCounter;

    public ComplexityScorer(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    public double score(List<ChatMessage> messages) {
        String user = MessageTexts.lastUser(messages).toLowerCase(Locale.ROOT);
        int userTokens = tokenCounter.count(user);
        int allTokens = tokenCounter.countMessages(messages);
        double length = 0.5 * lengthComponent(userTokens) + 0.5 * lengthComponent(allTokens);
        double keywords = 0;
        if (PROOF.matcher(user).find()) {
            keywords += 0.30;
        }
        if (REASONING.matcher(user).find()) {
            keywords += 0.16;
        }
        if (CODE.matcher(user).find()) {
            keywords += 0.22;
        }
        if (ANALYSIS.matcher(user).find()) {
            keywords += 0.16;
        }
        if (SYSTEMS.matcher(user).find()) {
            keywords += 0.12;
        }
        int questions = 0;
        for (int i = 0; i < user.length(); i++) {
            if (user.charAt(i) == '?') {
                questions++;
            }
        }
        if (questions >= 3) {
            keywords += 0.08;
        }
        keywords = Math.min(0.70, keywords);
        return Math.max(0, Math.min(1, length + keywords));
    }

    private static double lengthComponent(int tokens) {
        if (tokens <= 80) {
            return 0.05;
        }
        if (tokens <= 200) {
            return 0.12;
        }
        if (tokens <= 600) {
            return 0.22;
        }
        return 0.35;
    }
}
