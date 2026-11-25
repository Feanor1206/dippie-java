package com.dippie.assistant.chat;

import com.dippie.assistant.AssistantProperties;
import com.dippie.security.FixedWindowRateLimiter;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

// Chắn trước /api/chat bằng hai lớp: rate limit theo IP cùng userId và semaphore giới hạn số luồng SSE chạy song song.
@Component
public class ChatStreamGuard {

    public static final String RATE_LIMIT_MESSAGE =
            "Bạn gửi hơi nhanh rồi, chờ ít phút rồi hỏi lại CineBot nhé.";
    public static final String CONCURRENCY_LIMIT_MESSAGE =
            "Đang có nhiều người hỏi CineBot cùng lúc, bạn đợi một lát rồi thử lại nhé.";

    private static final long CONCURRENCY_RETRY_AFTER_SECONDS = 5L;

    private final FixedWindowRateLimiter clientRateLimiter;
    private final Semaphore concurrentStreams;
    private final int maxConcurrentStreams;

    public ChatStreamGuard(AssistantProperties properties, Clock clock) {
        AssistantProperties.Chat chat = properties.chat();
        AssistantProperties.ChatRateLimit rateLimit = chat.rateLimit();
        this.clientRateLimiter = new FixedWindowRateLimiter(
                rateLimit.maxRequests(),
                rateLimit.window(),
                rateLimit.maxClients(),
                clock
        );
        this.maxConcurrentStreams = chat.maxConcurrentStreams();
        this.concurrentStreams = new Semaphore(this.maxConcurrentStreams);
    }

    // Chặn theo IP trước rồi mới đếm theo userId để khách chưa đăng nhập cũng không spam được, qua hết mới lấy permit mở stream.
    public Permit acquire(String clientIp, Long userId) {
        String normalizedIp = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long ipRetryAfter = clientRateLimiter.retryAfterSeconds("ip:" + normalizedIp);
        if (ipRetryAfter > 0) {
            throw new ChatCapacityException(RATE_LIMIT_MESSAGE, ipRetryAfter);
        }
        if (userId != null) {
            long userRetryAfter = clientRateLimiter.retryAfterSeconds("user:" + userId);
            if (userRetryAfter > 0) {
                throw new ChatCapacityException(RATE_LIMIT_MESSAGE, userRetryAfter);
            }
        }
        if (!concurrentStreams.tryAcquire()) {
            throw new ChatCapacityException(CONCURRENCY_LIMIT_MESSAGE, CONCURRENCY_RETRY_AFTER_SECONDS);
        }
        return new Permit(concurrentStreams);
    }

    public int availablePermits() {
        return concurrentStreams.availablePermits();
    }

    public int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    // Dùng giữa các test để xóa bộ đếm tần suất cũ.
    public void resetRateLimit() {
        clientRateLimiter.reset();
    }

    // Việt để ý Permit dùng AtomicBoolean nhé, để dù close() có bị gọi lặp cũng chỉ release đúng một chỗ semaphore.
    public static final class Permit implements AutoCloseable {

        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
