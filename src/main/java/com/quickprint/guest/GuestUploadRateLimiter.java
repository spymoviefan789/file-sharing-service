package com.quickprint.guest;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Caps anonymous uploads per client so guest mode can't be scripted into a
// free bulk file host. Same fixed-window pattern as ReceiveRateLimiter.
// ponytail: second copy of the window logic; fold both into one shared
// FixedWindowRateLimiter if a third limiter ever appears.
@Component
public class GuestUploadRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MS = 60 * 60_000;

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
