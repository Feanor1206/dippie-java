package com.dippie.service.quiz;

import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.dto.quiz.QuizChallengeAnswerResponse;
import com.dippie.dto.quiz.QuizChallengeResponse;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ScreenTimeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// phát câu hỏi trắc nghiệm kiếm thêm phút xem phim khi hết giờ, tôn trọng trần thưởng ngày và giới hạn trả lời sai
@Service
public class QuizChallengeService {

    private static final String CHILD_ONLY_MESSAGE = "Thử thách câu hỏi chỉ áp dụng cho hồ sơ trẻ em.";
    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final ScreenTimeService screenTimeService;
    private final QuizSelectionService selectionService;
    private final QuizRateLimiter rateLimiter;
    private final Clock clock;

    public QuizChallengeService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            ScreenTimeService screenTimeService,
            QuizSelectionService selectionService,
            QuizRateLimiter rateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.screenTimeService = screenTimeService;
        this.selectionService = selectionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // phát 1 câu hỏi trắc nghiệm 4 lựa chọn cho bé khi hết giờ (chạm trần thưởng ngày trả 409)
    @Transactional(readOnly = true)
    public QuizChallengeResponse issueChallenge() {
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
                    "Bé đã nhận đủ số phút thưởng hôm nay rồi. Hãy nhờ ba mẹ cho thêm thời gian nhé."
            );
        }

        QuizSelectionService.IssuedQuizChallenge issued = selectionService.issueForChild(
                profile,
                QuizChallengeContext.SCREEN_TIME,
                "",
                QuizTokenCodec.STANDARD_TTL,
                false
        );
        return new QuizChallengeResponse(
                issued.token(),
                issued.question(),
                issued.options(),
                issued.subject().name(),
                issued.subject().label(),
                issued.grade().name(),
                issued.grade().label(),
                issued.setName(),
                profile.getRewardMinutes(),
                issued.expiresAt(),
                issued.expiresInSeconds(),
                summary.bonusRemainingSeconds()
        );
    }

    // chấm lựa chọn A-D bằng token dùng 1 lần, đúng thì cộng REWARD trong trần ngày còn sai thì hiện nhẹ đáp án đúng
    @Transactional
    public AnswerOutcome submitAnswer(QuizAnswerRequest request) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));

        QuizSelectionService.VerifiedAnswer verified = selectionService.verifyAndRecordAnswer(
                profileId,
                QuizChallengeContext.SCREEN_TIME,
                "",
                null,
                request,
                true
        );

        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!verified.correct()) {
            ScreenTimeSummary current = screenTimeService.summarize(profile, today);
            QuizChallengeAnswerResponse response = new QuizChallengeAnswerResponse(
                    false,
                    verified.correctOption(),
                    verified.correctText(),
                    verified.explanation(),
                    0,
                    0,
                    current.bonusCapReached(),
                    ScreenTimeStatusView.from(current)
            );
            return new AnswerOutcome(response, verified.gentleWrongMessage());
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
        QuizChallengeAnswerResponse response = new QuizChallengeAnswerResponse(
                true,
                verified.correctOption(),
                verified.correctText(),
                verified.explanation(),
                creditedSeconds,
                earnedMinutes,
                after.bonusCapReached(),
                ScreenTimeStatusView.from(after)
        );
        String message = creditedSeconds > 0
                ? "Đúng rồi! Bé được cộng thêm " + earnedMinutes + " phút xem phim."
                : "Đúng rồi! Nhưng hôm nay bé đã nhận đủ số phút thưởng rồi nhé.";
        return new AnswerOutcome(response, message);
    }

    public record AnswerOutcome(QuizChallengeAnswerResponse response, String message) {
    }
}
