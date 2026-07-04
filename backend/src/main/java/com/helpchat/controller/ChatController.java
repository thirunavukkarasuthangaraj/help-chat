package com.helpchat.controller;

import com.helpchat.config.RateLimiter;
import com.helpchat.model.Models.AppConfig;
import com.helpchat.model.Models.ChatRequest;
import com.helpchat.service.ChatService;
import com.helpchat.store.AppConfigStore;
import jakarta.servlet.http.HttpServletRequest;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ChatController.class);

    private final AppConfigStore apps;
    private final ChatService chatService;
    private final RateLimiter rateLimiter;
    // present only when storage=jdbc — used to persist feedback
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplate;
    // bounded: one thread per in-flight SSE reply, capped so a flood can't
    // exhaust server threads
    private final ExecutorService executor = Executors.newFixedThreadPool(64);

    @Value("${helpchat.max-message-length}")
    private int maxMessageLength;

    public ChatController(AppConfigStore apps, ChatService chatService, RateLimiter rateLimiter,
                          org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplate) {
        this.apps = apps;
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Liveness probe for load balancers / uptime monitors. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
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
    public SseEmitter message(@RequestBody ChatRequest req, HttpServletRequest http,
                              jakarta.servlet.http.HttpServletResponse response) {
        // Keep SSE streaming through reverse proxies (nginx buffers by default)
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        SseEmitter emitter = new SseEmitter(120_000L);

        AppConfig app = (req.appKey() == null) ? null : apps.get(req.appKey());
        if (app == null || !isValidSessionId(req.sessionId()) || req.message() == null
                || req.message().isBlank() || req.message().length() > maxMessageLength) {
            emitSafely(emitter, "error", "Invalid request.");
            emitter.complete();
            return emitter;
        }

        if (!rateLimiter.allow(req.sessionId() + "|" + http.getRemoteAddr())) {
            emitSafely(emitter, "error", "You're sending messages too quickly. Please wait a moment.");
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

    /**
     * 👍/👎 feedback on an answer. Always logged (grep FEEDBACK); also stored
     * in the chat_feedback table when storage=jdbc.
     */
    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody com.helpchat.model.Models.FeedbackRequest req) {
        boolean valid = req.appKey() != null && apps.get(req.appKey()) != null
                && isValidSessionId(req.sessionId())
                && ("up".equals(req.rating()) || "down".equals(req.rating()))
                && (req.comment() == null || req.comment().length() <= 512);
        if (!valid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid feedback"));
        }
        log.info("FEEDBACK appKey={} sessionId={} rating={} comment={}",
                req.appKey(), req.sessionId(), req.rating(),
                req.comment() == null ? "" : req.comment());
        // DB persistence is best-effort — feedback must never fail the request
        jdbcTemplate.ifAvailable(jdbc -> {
            try {
                jdbc.update(
                    "INSERT INTO chat_feedback (app_key, session_id, rating, comment) VALUES (?, ?, ?, ?)",
                    req.appKey(), req.sessionId(), req.rating(), req.comment());
            } catch (Exception e) {
                log.warn("feedback DB insert failed: {}", e.getMessage());
            }
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Session ids are widget-generated: short, alphanumeric. Reject anything else. */
    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && sessionId.length() <= 64
                && sessionId.matches("[A-Za-z0-9_-]+");
    }

    private void emitSafely(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ignored) {
            // client disconnected
        }
    }
}
