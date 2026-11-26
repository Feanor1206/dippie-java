package com.dippie.service.play;

import com.dippie.dto.play.WatchPetMedicineAnswerResponse;
import com.dippie.dto.play.WatchPetMedicineChallengeResponse;
import com.dippie.dto.play.WatchPetStatusView;
import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.ChildPlayState;
import com.dippie.model.play.WatchPetStage;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.quiz.QuizRateLimiter;
import com.dippie.service.quiz.QuizSelectionService;
import com.dippie.service.quiz.QuizTokenCodec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// xử lý toàn bộ nghiệp vụ Dieppie canh giờ nghỉ mắt: trạng thái, cho Dieppie ngủ và trả lời trắc nghiệm mua thuốc hồi sức
@Service
public class WatchPetService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String PET_DISABLED = "Tính năng Dieppie canh giờ nghỉ mắt đang tắt.";
    private static final String NOT_FAINTED = "Chưa đến giờ nghỉ mắt đâu, bé cứ xem tiếp nhé.";

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildPlayStateRepository stateRepository;
    private final PlayPlaybackGuard playbackGuard;
    private final QuizSelectionService quizSelectionService;
    private final QuizRateLimiter rateLimiter;
    private final Clock clock;

    public WatchPetService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildPlayStateRepository stateRepository,
            PlayPlaybackGuard playbackGuard,
            QuizSelectionService quizSelectionService,
            QuizRateLimiter rateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.playbackGuard = playbackGuard;
        this.quizSelectionService = quizSelectionService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // tổng hợp trạng thái Dieppie hiện tại, tự tính nghỉ 2 phút reset bộ đếm hoặc hết giờ ngủ là khỏe lại
    @Transactional(readOnly = true)
    public WatchPetStatusView buildStatus(Long profileId, ChildPlaySettings settings, LocalDateTime now) {
        if (!settings.isWatchPetEnabled()) {
            return WatchPetStatusView.disabled();
        }
        ChildPlayState state = stateRepository.findById(profileId).orElse(null);
        return toView(settings, state, now);
    }

    // bé chọn cho Dieppie ngủ X phút -> server khóa phát video/tiến độ cho tới khi hết giờ ngủ
    @Transactional
    public WatchPetStatusView startRest() {
        Viewer viewer = requireChildViewer();
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        if (!settings.isWatchPetEnabled()) {
            throw new InvalidRequestException(PET_DISABLED);
        }
        ChildPlayState state = stateRepository.findById(profile.getId())
                .orElseGet(() -> new ChildPlayState(profile.getId(), playbackGuard.randomLootThreshold(), now));
        state.clearExpiredRestIfNeeded(now);
        if (state.isRestingAt(now)) {
            return toView(settings, state, now);
        }
        if (!state.isPetFainted()) {
            throw new InvalidRequestException(NOT_FAINTED);
        }
        state.startRest(settings.getWatchPetRestMinutes(), now);
        ChildPlayState saved = stateRepository.saveAndFlush(state);
        return toView(settings, saved, now);
    }

    // phát câu hỏi trắc nghiệm 4 lựa chọn ký HMAC theo lớp của bé để mua thuốc hồi sức (chặn nếu Dieppie đang ngủ)
    @Transactional(readOnly = true)
    public WatchPetMedicineChallengeResponse issueMedicineChallenge() {
        Viewer viewer = requireChildViewer();
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        if (!settings.isWatchPetEnabled()) {
            throw new InvalidRequestException(PET_DISABLED);
        }
        ChildPlayState state = stateRepository.findById(profile.getId()).orElse(null);
        if (state != null && state.isRestingAt(now)) {
            throw new ConflictException("Đang trong giờ nghỉ cùng Dieppie, bé chờ hết giờ nghỉ nhé.");
        }
        boolean stillFainted = state != null
                && state.isPetFainted()
                && (state.getRestLockUntil() == null || now.isBefore(state.getRestLockUntil()));
        if (!stillFainted) {
            throw new InvalidRequestException(NOT_FAINTED);
        }

        QuizSelectionService.IssuedQuizChallenge issued = quizSelectionService.issueForChild(
                profile,
                QuizChallengeContext.WATCH_PET_MEDICINE,
                "",
                QuizTokenCodec.STANDARD_TTL,
                false
        );
        return new WatchPetMedicineChallengeResponse(
                issued.token(),
                issued.question(),
                issued.options(),
                issued.subject().name(),
                issued.subject().label(),
                issued.grade().name(),
                issued.grade().label(),
                issued.setName(),
                issued.expiresAt(),
                issued.expiresInSeconds()
        );
    }

    // chấm lựa chọn mua thuốc hồi sức: đúng thì reset đồng hồ liên tục và Dieppie khỏe lại ngay, sai thì tính vào rate limit
    @Transactional
    public MedicineOutcome submitMedicineAnswer(QuizAnswerRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        if (!settings.isWatchPetEnabled()) {
            throw new InvalidRequestException(PET_DISABLED);
        }
        ChildPlayState state = stateRepository.findById(profile.getId())
                .orElseGet(() -> new ChildPlayState(profile.getId(), playbackGuard.randomLootThreshold(), now));
        state.clearExpiredRestIfNeeded(now);
        if (state.isRestingAt(now)) {
            throw new ConflictException("Đang trong giờ nghỉ cùng Dieppie, bé chờ hết giờ nghỉ nhé.");
        }

        QuizSelectionService.VerifiedAnswer verified = quizSelectionService.verifyAndRecordAnswer(
                profileId,
                QuizChallengeContext.WATCH_PET_MEDICINE,
                "",
                null,
                request,
                true
        );

        if (!verified.correct()) {
            return new MedicineOutcome(
                    new WatchPetMedicineAnswerResponse(
                            false,
                            verified.correctOption(),
                            verified.correctText(),
                            verified.explanation(),
                            toView(settings, state, now)
                    ),
                    verified.gentleWrongMessage()
            );
        }

        state.healWithMedicine(now);
        ChildPlayState saved = stateRepository.saveAndFlush(state);
        return new MedicineOutcome(
                new WatchPetMedicineAnswerResponse(
                        true,
                        verified.correctOption(),
                        verified.correctText(),
                        verified.explanation(),
                        toView(settings, saved, now)
                ),
                "Giỏi quá! Đôi mắt đã thư giãn xong, bé xem tiếp cùng Dieppie nhé."
        );
    }

    private Viewer requireChildViewer() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException("Dieppie canh giờ nghỉ mắt chỉ áp dụng cho hồ sơ trẻ em.");
        }
        return viewer;
    }

    private static WatchPetStatusView toView(ChildPlaySettings settings, ChildPlayState state, LocalDateTime now) {
        int faintMinutes = settings.getWatchPetFaintMinutes();
        int restMinutes = settings.getWatchPetRestMinutes();
        int sweatyThreshold = WatchPetStage.sweatyThresholdSeconds(faintMinutes);
        int dizzyThreshold = WatchPetStage.dizzyThresholdSeconds(faintMinutes);
        int faintThreshold = WatchPetStage.faintThresholdSeconds(faintMinutes);
        if (state == null) {
            return new WatchPetStatusView(
                    true,
                    WatchPetStage.HAPPY,
                    0,
                    sweatyThreshold,
                    dizzyThreshold,
                    faintThreshold,
                    restMinutes,
                    false,
                    false,
                    null,
                    0L
            );
        }
        boolean resting = state.isRestingAt(now);
        boolean restExpired = state.getRestLockUntil() != null && !now.isBefore(state.getRestLockUntil());
        boolean fainted = !restExpired && state.isPetFainted();
        int continuous = (restExpired || (!resting && !fainted && state.hasPauseResetAt(now)))
                ? 0
                : state.getContinuousWatchSeconds();
        WatchPetStage stage = WatchPetStage.resolve(resting, fainted, continuous, faintMinutes);
        return new WatchPetStatusView(
                true,
                stage,
                continuous,
                sweatyThreshold,
                dizzyThreshold,
                faintThreshold,
                restMinutes,
                fainted || stage == WatchPetStage.FAINTED,
                resting,
                resting ? state.getRestLockUntil() : null,
                resting ? state.restRemainingSecondsAt(now) : 0L
        );
    }

    public record MedicineOutcome(WatchPetMedicineAnswerResponse response, String message) {
    }
}
