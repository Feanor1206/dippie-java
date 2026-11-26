package com.dippie.service.profile;

import com.dippie.dto.profile.MathChallengeAnswerRequest;
import com.dippie.dto.profile.MathChallengeAnswerResponse;
import com.dippie.dto.profile.MathChallengeResponse;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.MathChallengeGenerator;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// phát câu hỏi toán theo cấp độ của bé, chấm đáp án từ token ký HMAC, chống dùng lại và chặn đoán bừa
@Service
public class MathChallengeService {

    private static final String CHILD_ONLY_MESSAGE = "Thử thách toán chỉ áp dụng cho hồ sơ trẻ em.";
    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String TOKEN_USED_MESSAGE = "Câu hỏi này đã được trả lời rồi. Bé lấy câu mới nhé.";

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final ScreenTimeService screenTimeService;
    private final MathChallengeTokenCodec tokenCodec;
    private final MathChallengeRateLimiter rateLimiter;
    private final Clock clock;
    private final MathChallengeGenerator generator = new MathChallengeGenerator();
    private final Map<String, Instant> usedTokenIds = new LinkedHashMap<>();
    private final Object usedTokensLock = new Object();

    public MathChallengeService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            ScreenTimeService screenTimeService,
            MathChallengeTokenCodec tokenCodec,
            MathChallengeRateLimiter rateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.screenTimeService = screenTimeService;
        this.tokenCodec = tokenCodec;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // sinh câu hỏi toán đúng cấp độ bé và trả token mờ ký HMAC hạn 2 phút (đạt trần thưởng ngày thì báo 409)
    @Transactional(readOnly = true)
    public MathChallengeResponse issueChallenge() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        ScreenTimeSummary summary = screenTimeService.summarize(profile, today);
        if (summary.bonusCapReached()) {
            throw new ConflictException(
                    "Bé đã nhận đủ số phút thưởng từ giải toán hôm nay rồi. Hãy nhờ ba mẹ nhé."
            );
        }

        MathChallengeGenerator.MathQuestion generated = generator.generate(profile.getMathLevel());
        MathChallengeTokenCodec.IssuedToken issued = tokenCodec.issue(
                profile.getId(),
                generated.answer(),
                generated.level()
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        return new MathChallengeResponse(
                issued.token(),
                generated.question(),
                generated.level(),
                profile.getRewardMinutes(),
                expiresAtUtc,
                MathChallengeTokenCodec.TOKEN_TTL.toSeconds(),
                summary.bonusRemainingSeconds()
        );
    }

    // chấm đáp án kèm token 1 lần, đúng thì cộng REWARD trong trần ngày còn sai thì tính vào giới hạn đoán sai
    @Transactional
    public AnswerOutcome submitAnswer(MathChallengeAnswerRequest request) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        String rawToken = request == null ? null : request.token();
        Integer suppliedAnswer = request == null ? null : request.answer();
        if (suppliedAnswer == null) {
            throw new InvalidRequestException("Bé hãy nhập đáp án bằng số nhé.");
        }

        MathChallengeTokenCodec.DecodedToken decoded = tokenCodec.decode(rawToken)
                .orElseThrow(() -> new InvalidRequestException("Câu hỏi không hợp lệ hoặc đã hết hạn. Bé lấy câu mới nhé."));
        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId || !decoded.expiresAt().isAfter(nowInstant)) {
            throw new InvalidRequestException("Câu hỏi đã hết hạn hoặc không thuộc hồ sơ này. Bé lấy câu mới nhé.");
        }
        if (isTokenUsed(decoded.tokenId(), nowInstant)) {
            throw new InvalidRequestException(TOKEN_USED_MESSAGE);
        }

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);

        if (suppliedAnswer != decoded.answer()) {
            rateLimiter.recordFailure(profileId);
            ScreenTimeSummary current = screenTimeService.summarize(profile, today);
            MathChallengeAnswerResponse response = new MathChallengeAnswerResponse(
                    false,
                    0,
                    0,
                    current.bonusCapReached(),
                    ScreenTimeStatusView.from(current)
            );
            return new AnswerOutcome(response, "Chưa đúng rồi, bé thử tính lại nhé.");
        }

        // chỉ đánh dấu token đã dùng khi trả lời đúng để tránh nhận thưởng 2 lần trên cùng 1 token
        if (!tryRedeemToken(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException(TOKEN_USED_MESSAGE);
        }

        ScreenTimeSummary before = screenTimeService.summarize(profile, today);
        int creditedSeconds = (int) Math.min(profile.getRewardMinutes() * 60L, before.bonusRemainingSeconds());
        if (creditedSeconds > 0) {
            LocalDateTime nowUtc = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                    profile.getId(),
                    today,
                    ScreenTimeLedgerKind.REWARD,
                    creditedSeconds,
                    nowUtc
            ));
        }

        ScreenTimeSummary after = screenTimeService.summarize(profile, today);
        int earnedMinutes = creditedSeconds / 60;
        MathChallengeAnswerResponse response = new MathChallengeAnswerResponse(
                true,
                creditedSeconds,
                earnedMinutes,
                after.bonusCapReached(),
                ScreenTimeStatusView.from(after)
        );
        String message = creditedSeconds > 0
                ? "Đúng rồi! Bé được cộng thêm " + earnedMinutes + " phút xem phim."
                : "Đúng rồi! Nhưng hôm nay bé đã nhận đủ số phút thưởng từ giải toán rồi.";
        return new AnswerOutcome(response, message);
    }

    // dọn sạch bảng token đã dùng giữa các bài test
    public void resetUsedTokens() {
        synchronized (usedTokensLock) {
            usedTokenIds.clear();
        }
    }

    public int trackedUsedTokenCount() {
        synchronized (usedTokensLock) {
            pruneExpired(clock.instant());
            return usedTokenIds.size();
        }
    }

    private boolean isTokenUsed(String tokenId, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            return usedTokenIds.containsKey(tokenId);
        }
    }

    private boolean tryRedeemToken(String tokenId, Instant expiresAt, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            if (usedTokenIds.containsKey(tokenId)) {
                return false;
            }
            usedTokenIds.put(tokenId, expiresAt);
            return true;
        }
    }

    private void pruneExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = usedTokenIds.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    // gói payload chấm bài kèm câu phản hồi tiếng Việt cho controller
    public record AnswerOutcome(MathChallengeAnswerResponse response, String message) {
    }
}
