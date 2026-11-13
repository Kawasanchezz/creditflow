package com.creditflow.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Contador de falhas de autenticacao por origem (CWE-307): depois de 20 falhas em 5 minutos a origem recebe 429 sem
 * que o BCrypt seja executado, o que tambem evita usar o hash como amplificador de CPU. Em memoria: nao e distribuido
 * e, atras de proxy, a origem vista e a do proxy (limitacoes documentadas no SECURITY.md).
 */
@Component
public class AuthFailureThrottle {
    static final int MAX_FAILURES = 20;
    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean isBlocked(String origin) {
        Window w = windows.get(origin);
        if (w == null) {
            return false;
        }
        synchronized (w) {
            if (System.currentTimeMillis() - w.start > WINDOW_MS) {
                windows.remove(origin, w);
                return false;
            }
            return w.count >= MAX_FAILURES;
        }
    }

    public void recordFailure(String origin) {
        if (windows.size() >= MAX_TRACKED) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(e -> now - e.getValue().start > WINDOW_MS);
            if (windows.size() >= MAX_TRACKED) {
                windows.clear();
            }
        }
        long now = System.currentTimeMillis();
        windows.compute(origin, (k, w) -> {
            if (w == null || now - w.start > WINDOW_MS) {
                return new Window(now);
            }
            synchronized (w) {
                w.count++;
            }
            return w;
        });
    }

    public void reset() {
        windows.clear();
    }
}
