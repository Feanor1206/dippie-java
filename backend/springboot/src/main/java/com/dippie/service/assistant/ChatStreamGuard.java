package com.dippie.service.assistant;

import com.dippie.config.assistant.AssistantProperties;
import com.dippie.exception.ChatCapacityException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

// chắn trước /api/chat 2 lớp, rate limit theo IP với userId rồi thêm semaphore chặn số luồng SSE chạy song song
@Component
public class ChatStreamGuard {

    public static final String RATE_LIMIT_MESSAGE =
            "Bạn gửi hơi nhanh rồi, chờ ít phút rồi hỏi lại CineBot nhé.";
    public static final String CONCURRENCY_LIMIT_MESSAGE =
            "Đang có nhiều người hỏi CineBot cùng lúc, bạn đợi một lát rồi thử lại nhé.";


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

    // check IP trước rồi mới đếm userId để khách chưa đăng nhập cũng khỏi spam, qua hết mới cấp permit mở stream
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
            throw new ChatCapacityException(CONCURRENCY_LIMIT_MESSAGE, 5L);
        }
        return new Permit(concurrentStreams);
    }

    public int availablePermits() {
        return concurrentStreams.availablePermits();
    }

    public int maxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    // dùng giữa mấy bài test để xóa bộ đếm rate limit cũ
    public void resetRateLimit() {
        clientRateLimiter.reset();
    }

    // Việt để ý Permit dùng AtomicBoolean nha, có gọi close() lặp cũng chỉ release đúng 1 slot semaphore thôi
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
