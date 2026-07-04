package com.helpchat.service;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pluggable answer engine. The widget and REST API never depend on which
 * implementation is active — swap engines via `helpchat.provider` in
 * application.yml (or the HELPCHAT_PROVIDER environment variable):
 *
 *   docs   (default)  answers straight from the app's help docs; no external
 *                     services, no API keys, zero cost
 *   claude (optional) generates answers with the Anthropic API; requires
 *                     ANTHROPIC_API_KEY
 *
 * Add your own engine (your existing FAQ service, a database lookup, any
 * other API) by implementing this interface and returning it from
 * ChatService's provider selection.
 */
public interface AnswerProvider {

    /**
     * Produce the assistant's reply for one turn.
     *
     * @param app         the application this chat belongs to
     * @param docsContext top-matching help-doc sections for the question ("" if none)
     * @param history     recent conversation including the new user message (last entry)
     * @param onDelta     push text chunks here as they become available (streamed to the widget)
     * @return the full reply text (persisted as history)
     */
    String answer(AppConfig app, String docsContext, List<ChatMessage> history,
                  Consumer<String> onDelta) throws Exception;
}
