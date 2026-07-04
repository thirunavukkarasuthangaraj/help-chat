package com.helpchat.service;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Default answer engine — no AI, no external services, no API keys.
 *
 * Answers by returning the best-matching sections of the app's help docs
 * (already selected by DocsRetriever via keyword overlap). Works well when
 * each `## Heading` section of the docs file answers one question directly.
 *
 * Every question that finds no answer is logged ("UNANSWERED") — review
 * those logs to learn which sections to add to your help docs.
 */
@Service
public class DocsAnswerProvider implements AnswerProvider {

    private static final Logger log = LoggerFactory.getLogger(DocsAnswerProvider.class);

    private static final Pattern GREETING = Pattern.compile(
            "^\\s*(hi+|hii+|hello+|hey+|hai|yo|vanakkam|namaste|namaskar|hola|"
            + "good\\s*(morning|afternoon|evening)|greetings)\\s*[!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THANKS = Pattern.compile(
            "^\\s*(thanks?|thank\\s*you|thx|ty|nandri|super|great|ok+|okay)\\s*[!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String answer(AppConfig app, String docsContext, List<ChatMessage> history,
                         Consumer<String> onDelta) {
        String question = history.isEmpty() ? "" : history.get(history.size() - 1).content();

        String reply;
        // Small-talk first — "hi" or "thanks" should never keyword-match a doc section
        if (GREETING.matcher(question).matches()) {
            reply = (app.welcomeMessage() != null && !app.welcomeMessage().isBlank())
                    ? app.welcomeMessage()
                    : "Hello! How can I help you today?";
        } else if (THANKS.matcher(question).matches()) {
            reply = "You're welcome! Ask me anytime if you have more questions.";
        } else if (docsContext == null || docsContext.isBlank()) {
            log.info("UNANSWERED appKey={} question=\"{}\"", app.appKey(), question);
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
