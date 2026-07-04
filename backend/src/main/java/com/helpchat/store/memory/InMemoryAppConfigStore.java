package com.helpchat.store.memory;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.store.AppConfigStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default app registry — entries live in code, no database needed.
 * Active when helpchat.storage=memory (the default).
 */
@Component
@ConditionalOnProperty(name = "helpchat.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryAppConfigStore implements AppConfigStore {

    private final Map<String, AppConfig> apps = new ConcurrentHashMap<>();

    public InMemoryAppConfigStore() {
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

        // ---- GHSS Mittur school website ----
        apps.put("mitturschool", new AppConfig(
                "mitturschool",
                "GHSS Mittur",
                "#1a5d1a",
                "Vanakkam! I'm the GHSS Mittur help assistant. Ask me about admission, contact, facilities, or events.",
                List.of("How do I apply for admission?",
                        "What is the school's phone number?",
                        "What facilities does the school have?"),
                """
                You are a friendly, concise help assistant for GHSS Mittur
                (Government Higher Secondary School, Mittur, Tamil Nadu).
                Rules:
                - Answer ONLY using the provided help documentation context.
                - If the answer is not in the docs, say you don't have that information
                  and suggest calling the school office.
                - Keep answers short and simple.
                """,
                "docs/mitturschool.md"
        ));

        // ---- Add your real apps below ----
    }

    @Override
    public AppConfig get(String appKey) {
        return apps.get(appKey);
    }
}
