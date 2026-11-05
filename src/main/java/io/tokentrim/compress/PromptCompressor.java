package io.tokentrim.compress;

import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.api.dto.TokenTrimOptions;
import io.tokentrim.config.TokenTrimProperties;
import io.tokentrim.embed.MessageTexts;
import io.tokentrim.rag.RagTrimmer;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a raw chat into the prompt that will actually be sent:
 * extract inline documents, normalize whitespace, drop boilerplate, summarize old turns,
 * then attach only the RAG excerpts that matched the question.
 */
public final class PromptCompressor {

    private final TokenTrimProperties properties;
    private final WhitespaceNormalizer whitespace;
    private final BoilerplateStripper boilerplate;
    private final HistoryBudgetTrimmer history;
    private final DocumentExtractor documents;
    private final RagTrimmer rag;

    public PromptCompressor(
            TokenTrimProperties properties,
            WhitespaceNormalizer whitespace,
            BoilerplateStripper boilerplate,
            HistoryBudgetTrimmer history,
            DocumentExtractor documents,
            RagTrimmer rag
    ) {
        this.properties = properties;
        this.whitespace = whitespace;
        this.boilerplate = boilerplate;
        this.history = history;
        this.documents = documents;
        this.rag = rag;
    }

    public PreparedPrompt prepare(List<ChatMessage> input, TokenTrimOptions options) {
        List<ChatMessage> source = input == null ? List.of() : input;
        boolean compress = properties.getCompression().isEnabled() && (options == null || !options.isSkipCompression());
        boolean useRag = properties.getRag().isEnabled();
        List<String> docs = new ArrayList<>();
        if (options != null && options.getDocuments() != null) {
            for (String document : options.getDocuments()) {
                if (document != null && !document.isBlank()) {
                    docs.add(document.strip());
                }
            }
        }

        List<ChatMessage> working = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            ChatMessage message = source.get(i);
            boolean last = i == source.size() - 1;
            if (useRag && message.isPlainText()) {
                DocumentExtractor.Extraction extraction = documents.extract(message.text());
                docs.addAll(extraction.documents());
                message = message.withText(extraction.remaining());
            }
            if (compress && message.isPlainText()) {
                String text = whitespace.normalize(message.text());
                String role = message.getRole();
                if ("assistant".equals(role) || "system".equals(role)) {
                    text = whitespace.normalize(boilerplate.strip(text));
                }
                message = message.withText(text);
            }
            if (message.isPlainText() && message.text().isBlank() && !last) {
                continue;
            }
            working.add(message);
        }

        if (compress) {
            int budget = options != null && options.getHistoryTokenBudget() != null
                    ? options.getHistoryTokenBudget()
                    : properties.getCompression().getHistoryTokenBudget();
            working = history.trim(working, budget);
        }

        int chunks = 0;
        if (useRag && !docs.isEmpty()) {
            String query = MessageTexts.lastUser(working);
            RagTrimmer.Selection selection = rag.select(docs, query, options == null ? null : options.getRagTopK());
            chunks = selection.chunks().size();
            if (!selection.rendered().isBlank()) {
                working = insertContext(working, ChatMessage.system(selection.rendered()));
            }
        }
        return new PreparedPrompt(List.copyOf(working), docs.size(), chunks);
    }

    static List<ChatMessage> insertContext(List<ChatMessage> messages, ChatMessage context) {
        List<ChatMessage> out = new ArrayList<>();
        int index = 0;
        while (index < messages.size() && "system".equals(messages.get(index).getRole())) {
            out.add(messages.get(index));
            index++;
        }
        out.add(context);
        while (index < messages.size()) {
            out.add(messages.get(index));
            index++;
        }
        return out;
    }
}
