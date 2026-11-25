package com.dippie.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final int maxClients;
    private final Clock clock;
    private final Map<ClientKey, AttemptWindow> clients = new LinkedHashMap<>();
    private final Object lock = new Object();

    public LoginRateLimiter(RateLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.maxAttempts();
        this.window = properties.window();
        this.maxClients = properties.maxClients();
        this.clock = clock;
    }

    public long retryAfterSeconds(String clientIp, String identifier) {
        ClientKey key = ClientKey.of(clientIp, identifier);
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        synchronized (lock) {
            evictExpired(cutoff);
            AttemptWindow current = clients.get(key);
            if (current != null && current.count() >= maxAttempts) {
                long remainingMillis = Duration.between(now, current.startedAt().plus(window)).toMillis();
                long remainingSeconds = Math.floorDiv(remainingMillis, 1000L);
                return Math.max(1L, remainingSeconds + 1L);
            }
            if (current == null) {
                if (clients.size() >= maxClients) {
                    evictOldest();
                }
                clients.put(key, new AttemptWindow(now, 1));
            } else {
                clients.put(key, current.increment());
            }
            return 0L;
        }
    }

    public void reset() {
        synchronized (lock) {
            clients.clear();
        }
    }

    int trackedClientCount() {
        synchronized (lock) {
            return clients.size();
        }
    }

    private void evictExpired(Instant cutoff) {
        Iterator<Map.Entry<ClientKey, AttemptWindow>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ClientKey, AttemptWindow> entry = iterator.next();
            if (!entry.getValue().startedAt().isAfter(cutoff)) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    private void evictOldest() {
        Iterator<Map.Entry<ClientKey, AttemptWindow>> iterator = clients.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record ClientKey(String clientIp, String identifier) {

        private static ClientKey of(String clientIp, String identifier) {
            String normalizedIp = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
            String normalizedIdentifier = identifier == null
                    ? ""
                    : identifier.trim().toLowerCase(Locale.ROOT);
            return new ClientKey(normalizedIp, normalizedIdentifier);
        }
    }

    private record AttemptWindow(Instant startedAt, int count) {

        private AttemptWindow increment() {
            return new AttemptWindow(startedAt, count + 1);
        }
    }
}
