package io.tokentrim.compress;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.token.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryBudgetTrimmerTest {

    private final TokenCounter tokenCounter = new TokenCounter("cl100k_base");
    private final HistoryBudgetTrimmer trimmer = new HistoryBudgetTrimmer(tokenCounter, 120);

    @Test
    void dropsOldTurnsKeepsLatestUserAndStaysWithinBudget() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are concise."));
        for (int i = 0; i < 6; i++) {
            messages.add(ChatMessage.user("Question " + i + " " + "background ".repeat(40)));
            messages.add(ChatMessage.assistant("Answer " + i + " " + "detail ".repeat(40)));
        }
        messages.add(ChatMessage.user("Final question about Paris?"));

        List<ChatMessage> trimmed = trimmer.trim(messages, 350);

        assertThat(trimmed.get(trimmed.size() - 1).text()).isEqualTo("Final question about Paris?");
        assertThat(trimmed.stream().filter(message -> "user".equals(message.getRole())).count()).isLessThan(7);
        assertThat(trimmed.stream().anyMatch(message -> message.text().contains("Earlier conversation, condensed"))).isTrue();
        assertThat(tokenCounter.countMessages(trimmed)).isLessThanOrEqualTo(350);
        assertThat(trimmed.get(0).text()).isEqualTo("You are concise.");
    }
}
