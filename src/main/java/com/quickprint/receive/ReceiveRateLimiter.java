package com.quickprint.receive;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Caps how many receive attempts one client can make per minute, so a guessable
// nickname's rolling code can't be brute-forced.
// ponytail: in-memory fixed window, per instance, no eviction. Fine for the
// single-instance MVP; a shared store (Redis) would be needed once scaled out.
@Component
public class ReceiveRateLimiter {

    private static final int MAX_PER_WINDOW = 20;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_MS) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });
        return window.count <= MAX_PER_WINDOW;
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
