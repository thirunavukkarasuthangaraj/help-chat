package com.helpchat.service;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatMessage;
import com.helpchat.store.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Orchestrates one chat turn: history -> docs retrieval -> Claude -> save. */
@Service
public class ChatService {

    private final SessionStore sessions;
    private final DocsRetriever retriever;
    private final ClaudeClient claude;

    @Value("${helpchat.history-limit}")
    private int historyLimit;

    public ChatService(SessionStore sessions, DocsRetriever retriever, ClaudeClient claude) {
        this.sessions = sessions;
        this.retriever = retriever;
        this.claude = claude;
    }

    public void handleTurn(AppConfig app, String sessionId, String userMessage,
                           Consumer<String> onDelta) throws Exception {

        // 1. Retrieve relevant help-doc sections for this question
        String docsContext = retriever.retrieve(app.docsFile(), userMessage, 4);

        // 2. Build system prompt = app persona + retrieved docs
        String systemPrompt = app.systemPrompt()
                + "\n\n<help_documentation>\n"
                + (docsContext.isBlank() ? "(no matching documentation found)" : docsContext)
                + "\n</help_documentation>";

        // 3. Conversation = recent history + new user message
        List<ChatMessage> history = new ArrayList<>(sessions.getHistory(sessionId, historyLimit));
        history.add(new ChatMessage("user", userMessage));

        // 4. Stream from Claude
        String reply = claude.streamCompletion(systemPrompt, history, onDelta);

        // 5. Persist the turn
        sessions.append(sessionId, new ChatMessage("user", userMessage));
        sessions.append(sessionId, new ChatMessage("assistant", reply));
    }
}
