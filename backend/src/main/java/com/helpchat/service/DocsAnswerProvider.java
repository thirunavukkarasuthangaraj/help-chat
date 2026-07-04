package com.helpchat.service;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Default answer engine — no AI, no external services, no API keys.
 *
 * Answers by returning the best-matching sections of the app's help docs
 * (already selected by DocsRetriever via keyword overlap). Works well when
 * each `## Heading` section of resources/docs/<appkey>.md answers one
 * question directly, FAQ-style.
 */
@Service
public class DocsAnswerProvider implements AnswerProvider {

    @Override
    public String answer(AppConfig app, String docsContext, List<ChatMessage> history,
                         Consumer<String> onDelta) {
        String reply;
        if (docsContext == null || docsContext.isBlank()) {
            reply = "Sorry, I couldn't find anything about that in the help topics. "
                    + "Try different words, or pick one of the suggested questions.";
        } else {
            reply = formatSections(docsContext);
        }
        onDelta.accept(reply);
        return reply;
    }

    /** Turn "## Heading\nbody" markdown sections into readable chat text. */
    private String formatSections(String docsContext) {
        StringBuilder out = new StringBuilder();
        for (String section : docsContext.split("\\n\\n---\\n\\n")) {
            String s = section.strip();
            if (s.isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(s.replaceFirst("(?m)^## (.+)$", "$1\n"));
        }
        return out.toString();
    }
}
