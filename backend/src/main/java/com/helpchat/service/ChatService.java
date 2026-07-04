package com.helpchat.service;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatMessage;
import com.helpchat.store.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates one chat turn: history -> docs retrieval -> answer engine -> save.
 *
 * The answer engine is pluggable (helpchat.provider):
 *   docs   (default)  answers from help docs only — no external services
 *   claude (optional) AI-generated answers via the Anthropic API
 */
@Service
public class ChatService {

    private final SessionStore sessions;
    private final DocsRetriever retriever;
    private final DocsAnswerProvider docsProvider;
    private final ClaudeClient claudeProvider;

    @Value("${helpchat.history-limit}")
    private int historyLimit;

    @Value("${helpchat.provider:docs}")
    private String provider;

    public ChatService(SessionStore sessions, DocsRetriever retriever,
                       DocsAnswerProvider docsProvider, ClaudeClient claudeProvider) {
        this.sessions = sessions;
        this.retriever = retriever;
        this.docsProvider = docsProvider;
        this.claudeProvider = claudeProvider;
    }

    public void handleTurn(AppConfig app, String sessionId, String userMessage,
                           Consumer<String> onDelta) throws Exception {

        // 1. Retrieve relevant help-doc sections for this question
        String docsContext = retriever.retrieve(app.docsFile(), userMessage, 4);

        // 2. Conversation = recent history + new user message
        List<ChatMessage> history = new ArrayList<>(sessions.getHistory(sessionId, historyLimit));
        history.add(new ChatMessage("user", userMessage));

        // 3. Produce the reply with the configured engine
        String reply = answerProvider().answer(app, docsContext, history, onDelta);

        // 4. Persist the turn
        sessions.append(sessionId, new ChatMessage("user", userMessage));
        sessions.append(sessionId, new ChatMessage("assistant", reply));
    }

    private AnswerProvider answerProvider() {
        return "claude".equalsIgnoreCase(provider) ? claudeProvider : docsProvider;
    }
}
