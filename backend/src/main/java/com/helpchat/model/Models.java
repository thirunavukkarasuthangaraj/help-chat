package com.helpchat.model;

import java.util.List;
import java.util.Map;

/** All DTOs in one file to keep the starter kit compact. Split later if you prefer. */
public class Models {

    /**
     * Incoming chat request from the widget.
     * user/context are optional — set via HelpChat.identify()/setContext() in the
     * widget. Use them for personalization, analytics, or routing; safe to ignore.
     */
    public record ChatRequest(String appKey, String sessionId, String message,
                              Map<String, Object> user, Map<String, Object> context) {}

    /** 👍/👎 on an answer. rating = "up" | "down". */
    public record FeedbackRequest(String appKey, String sessionId, String rating, String comment) {}

    /** One message in a conversation. role = "user" | "assistant" */
    public record ChatMessage(String role, String content) {}

    /** Per-application configuration. Add a row per app to reuse across apps. */
    public record AppConfig(
            String appKey,
            String appName,
            String themeColor,        // primary color for the widget
            String welcomeMessage,
            List<String> suggestedQuestions,
            String systemPrompt,      // AI persona + rules for this app
            String docsFile           // classpath docs file used for retrieval
    ) {}
}
