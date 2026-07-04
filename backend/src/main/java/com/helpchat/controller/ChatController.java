package com.helpchat.controller;

import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatRequest;
import com.helpchat.service.ChatService;
import com.helpchat.store.AppConfigStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final AppConfigStore apps;
    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${helpchat.max-message-length}")
    private int maxMessageLength;

    public ChatController(AppConfigStore apps, ChatService chatService) {
        this.apps = apps;
        this.chatService = chatService;
    }

    /** Widget bootstrap: theme, welcome message, suggested questions. */
    @GetMapping("/config/{appKey}")
    public ResponseEntity<?> config(@PathVariable String appKey) {
        AppConfig app = apps.get(appKey);
        if (app == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Unknown appKey"));
        }
        return ResponseEntity.ok(Map.of(
                "appName", app.appName(),
                "themeColor", app.themeColor(),
                "welcomeMessage", app.welcomeMessage(),
                "suggestedQuestions", app.suggestedQuestions()
        ));
    }

    /**
     * Chat turn with a streamed (SSE) reply.
     * The widget POSTs {appKey, sessionId, message} and reads text/event-stream.
     */
    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        AppConfig app = (req.appKey() == null) ? null : apps.get(req.appKey());
        if (app == null || req.sessionId() == null || req.message() == null
                || req.message().isBlank() || req.message().length() > maxMessageLength) {
            emitSafely(emitter, "error", "Invalid request.");
            emitter.complete();
            return emitter;
        }

        executor.submit(() -> {
            try {
                chatService.handleTurn(app, req.sessionId(), req.message().strip(),
                        delta -> emitSafely(emitter, "delta", delta));
                emitSafely(emitter, "done", "");
                emitter.complete();
            } catch (Exception e) {
                emitSafely(emitter, "error", "Something went wrong. Please try again.");
                emitter.complete();
            }
        });
        return emitter;
    }

    private void emitSafely(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ignored) {
            // client disconnected
        }
    }
}
