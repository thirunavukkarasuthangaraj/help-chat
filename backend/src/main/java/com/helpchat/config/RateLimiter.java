package com.helpchat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sliding-window rate limiter: at most N messages per minute per
 * client (sessionId + IP). Protects the shared multi-app server from a
 * single user or buggy page flooding it — especially important when the
 * claude engine (paid per message) is enabled.
 *
 * Configure with helpchat.rate-limit-per-minute (0 disables).
 */
@Component
public class RateLimiter {

    private record Window(long startMillis, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${helpchat.rate-limit-per-minute:20}")
    private int maxPerMinute;

    /** @return true if this client is within the limit and may proceed. */
    public boolean allow(String clientKey) {
        if (maxPerMinute <= 0) return true;
        long now = System.currentTimeMillis();
        Window w = windows.compute(clientKey, (k, cur) ->
                (cur == null || now - cur.startMillis() >= 60_000)
                        ? new Window(now, new AtomicInteger(0))
                        : cur);
        return w.count().incrementAndGet() <= maxPerMinute;
    }

    /** Drop stale windows every 5 minutes so the map never grows unbounded. */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - 120_000;
        windows.entrySet().removeIf(e -> e.getValue().startMillis() < cutoff);
    }
}
