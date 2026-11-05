package io.tokentrim;

import io.tokentrim.api.dto.ChatCompletionRequest;
import io.tokentrim.api.dto.ChatMessage;
import io.tokentrim.api.dto.TokenTrimOptions;

import java.util.ArrayList;
import java.util.List;

public final class FixturePrompts {

    private FixturePrompts() {
    }

    public static ChatCompletionRequest verboseEasy() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("""
                You are a helpful assistant.   Please note that you should answer briefly.


                It is important to note that accuracy matters.

                %s
                """.formatted("Policy boilerplate. ".repeat(40))));
        for (int i = 0; i < 8; i++) {
            messages.add(ChatMessage.user("Earlier question number " + i + ". "
                    + "Could you elaborate on miscellaneous background context? ".repeat(12)));
            messages.add(ChatMessage.assistant("Sure, I'd be happy to help! Here is a long answer that repeats itself. ".repeat(18)
                    + " I hope this helps! Let me know if you need anything else."));
        }
        messages.add(ChatMessage.user("What is the capital of France?"));
        request.setMessages(messages);
        TokenTrimOptions options = new TokenTrimOptions();
        options.setDocuments(List.of(franceDocument(), germanyDocument(), breadDocument(), oceanDocument()));
        request.setTokentrim(options);
        return request;
    }

    public static ChatCompletionRequest hard() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(ChatMessage.user(
                "Prove step by step the time complexity of merge sort and implement the algorithm in Python.")));
        return request;
    }

    public static String franceDocument() {
        return """
                The capital of France is Paris. Paris sits on the Seine in northern France and is the country's
                political center. Visitors associate Paris with the Louvre, the Seine, and Haussmann boulevards.
                France's capital city has been Paris since the medieval monarchy consolidated power there.
                """ + " Paris remains the capital of France. ".repeat(40);
    }

    public static String germanyDocument() {
        return """
                The capital of Germany is Berlin. Berlin is a large city in the northeast of the country with a
                long political history. Germany's government sits in Berlin.
                """ + " Berlin is the capital of Germany. ".repeat(40);
    }

    public static String breadDocument() {
        return """
                Sourdough bread uses flour, water, and salt. Stretch and fold the dough during bulk fermentation,
                then bake it in a hot Dutch oven until the crust is dark.
                """ + " Feed the sourdough starter with flour. ".repeat(40);
    }

    public static String oceanDocument() {
        return """
                The Pacific Ocean is the largest ocean on Earth. Currents move heat across the basin and shape
                coastal climates from Chile to Japan.
                """ + " Ocean currents circulate through the Pacific. ".repeat(30);
    }
}
