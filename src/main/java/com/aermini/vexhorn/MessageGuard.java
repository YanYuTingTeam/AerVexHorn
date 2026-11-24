package com.aermini.vexhorn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Resource limits for untrusted incoming messages. */
final class MessageGuard {
    static final int MAX_QUEUED_PER_PLAYER = 20;
    private static final int MAX_TEXT_CHARS = 512;
    private static final int MAX_LINES = 4;
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private final Map<UUID, Window> sources = new HashMap<>();
    private final Window total = new Window();

    synchronized boolean allowPacket(UUID source, long now) {
        Window window = sources.get(source);
        if (window == null) {
            window = new Window();
            sources.put(source, window);
        }
        return window.take(now, 20) && total.take(now, 100);
    }

    synchronized void removePlayer(UUID source) {
        sources.remove(source);
    }

    static boolean isTextAllowed(String text) {
        if (text.isEmpty() || text.length() > MAX_TEXT_CHARS) return false;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' && ++lines > MAX_LINES) return false;
            if ((c < 32 && c != '\n' && c != '\r') || c == 127) return false;
        }
        return true;
    }

    private static final class Window {
        long start;
        int count;
        boolean take(long now, int limit) {
            if (count == 0 || now - start >= WINDOW_NANOS || now - start < 0) {
                start = now;
                count = 0;
            }
            if (count >= limit) return false;
            count++;
            return true;
        }
    }
}
