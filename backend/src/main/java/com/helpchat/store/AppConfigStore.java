package com.helpchat.store;

import com.helpchat.model.Models.AppConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of applications that can use the chat widget.
 * To onboard a new app: add one entry here (or later, one row in DynamoDB).
 *
 * PRODUCTION: replace this with a DynamoDB table `chat_apps` keyed by appKey.
 */
@Component
public class AppConfigStore {

    private final Map<String, AppConfig> apps = new ConcurrentHashMap<>();

    public AppConfigStore() {
        // ---- Demo app 1 ----
        apps.put("demo", new AppConfig(
                "demo",
                "Demo App",
                "#0d7377",
                "Hi! I'm your help assistant. Ask me anything about this app.",
                List.of("How do I get started?", "How do I reset my password?", "What are the pricing plans?"),
                """
                You are a friendly, concise help assistant for "Demo App".
                Rules:
                - Answer ONLY using the provided help documentation context.
                - If the answer is not in the docs, say you don't have that information and suggest contacting support.
                - Keep answers short (2-5 sentences). Use simple language.
                - Never invent features, prices, or steps.
                """,
                "docs/demo.md"
        ));

        // ---- Add your real apps below, e.g. ----
        // apps.put("myapp-web", new AppConfig("myapp-web", "My App", "#0d7377", ...));
    }

    public AppConfig get(String appKey) {
        return apps.get(appKey);
    }
}
