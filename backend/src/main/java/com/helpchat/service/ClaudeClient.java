package com.helpchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helpchat.model.Models.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal Anthropic Messages API client using Java's built-in HttpClient.
 * Streams text deltas to a consumer so the widget can render tokens live.
 */
@Service
public class ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${helpchat.anthropic-api-key}")
    private String apiKey;

    @Value("${helpchat.model}")
    private String model;

    @Value("${helpchat.max-tokens}")
    private int maxTokens;

    /**
     * Sends the conversation to Claude with streaming enabled and pushes each
     * text delta to onDelta. Returns the full assembled response text.
     */
    public String streamCompletion(String systemPrompt,
                                   List<ChatMessage> history,
                                   Consumer<String> onDelta) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            String msg = "Server is missing ANTHROPIC_API_KEY. Set the environment variable and restart.";
            onDelta.accept(msg);
            return msg;
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);
        body.put("system", systemPrompt);
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : history) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            msg.put("content", m.content());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<java.io.InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        StringBuilder full = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            if (response.statusCode() != 200) {
                StringBuilder err = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) err.append(line);
                String msg = "AI service error (" + response.statusCode() + "). Please try again.";
                System.err.println("Anthropic API error: " + err);
                onDelta.accept(msg);
                return msg;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty() || data.equals("[DONE]")) continue;
                JsonNode event = mapper.readTree(data);
                if ("content_block_delta".equals(event.path("type").asText())) {
                    String text = event.path("delta").path("text").asText("");
                    if (!text.isEmpty()) {
                        full.append(text);
                        onDelta.accept(text);
                    }
                }
            }
        }
        return full.toString();
    }
}
