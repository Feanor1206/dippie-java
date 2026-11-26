package com.dippie.service.play;

import com.dippie.dto.play.WatchPetMedicineAnswerRequest;
import com.dippie.dto.play.WatchPetMedicineAnswerResponse;
import com.dippie.dto.play.WatchPetMedicineChallengeResponse;
import com.dippie.dto.play.WatchPetStatusView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.ChildPlayState;
import com.dippie.model.play.WatchPetStage;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.MathChallengeGenerator;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.MathChallengeRateLimiter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// xử lý toàn bộ nghiệp vụ Dieppie canh giờ nghỉ mắt: trạng thái, cho Dieppie ngủ 5 phút và giải toán mua thuốc hồi sức
@Service
public class WatchPetService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String PET_DISABLED = "Tính năng Dieppie canh giờ nghỉ mắt đang tắt.";
    private static final String NOT_FAINTED = "Dieppie vẫn đang khỏe, chưa cần nghỉ hay uống thuốc đâu.";
    private static final Duration MEDICINE_TTL = Duration.ofMinutes(2);

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildPlayStateRepository stateRepository;
    private final PlayPlaybackGuard playbackGuard;
    private final PlayTokenCodec tokenCodec;
    private final MathChallengeRateLimiter rateLimiter;
    private final Clock clock;
    private final MathChallengeGenerator generator = new MathChallengeGenerator();

    public WatchPetService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildPlayStateRepository stateRepository,
            PlayPlaybackGuard playbackGuard,
            PlayTokenCodec tokenCodec,
            MathChallengeRateLimiter rateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.playbackGuard = playbackGuard;
        this.tokenCodec = tokenCodec;
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

    // phát câu hỏi toán ký HMAC theo cấp độ bé để mua thuốc hồi sức (chặn nếu bé đã chọn cho Dieppie ngủ)
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
            throw new ConflictException("Dieppie đang ngủ nghỉ mắt rồi, bé chờ hết giờ nghỉ nhé.");
        }
        boolean stillFainted = state != null
                && state.isPetFainted()
                && (state.getRestLockUntil() == null || now.isBefore(state.getRestLockUntil()));
        if (!stillFainted) {
            throw new InvalidRequestException(NOT_FAINTED);
        }

        MathChallengeGenerator.MathQuestion generated = generator.generate(profile.getMathLevel());
        PlayTokenCodec.IssuedToken issued = tokenCodec.issue(
                PlayTokenCodec.KIND_MEDICINE_MATH,
                profile.getId(),
                MEDICINE_TTL,
                generated.answer() + ":" + generated.level()
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        return new WatchPetMedicineChallengeResponse(
                issued.token(),
                generated.question(),
                generated.level(),
                expiresAtUtc,
                MEDICINE_TTL.toSeconds()
        );
    }

    // chấm bài toán mua thuốc hồi sức: đúng thì reset đồng hồ liên tục và Dieppie khỏe lại ngay, sai thì ghi nhận vào bộ giới hạn đoán sai
    @Transactional
    public MedicineOutcome submitMedicineAnswer(WatchPetMedicineAnswerRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();
        rateLimiter.ensureNotLocked(profileId);

        Integer suppliedAnswer = request == null ? null : request.answer();
        if (suppliedAnswer == null) {
            throw new InvalidRequestException("Bé hãy nhập đáp án bằng số nhé.");
        }
        PlayTokenCodec.DecodedToken decoded = tokenCodec.decode(
                        request.token(),
                        PlayTokenCodec.KIND_MEDICINE_MATH
                )
                .orElseThrow(() -> new InvalidRequestException("Câu hỏi không hợp lệ hoặc đã hết hạn. Bé lấy câu mới nhé."));
        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId || !decoded.expiresAt().isAfter(nowInstant)) {
            throw new InvalidRequestException("Câu hỏi đã hết hạn hoặc không thuộc hồ sơ này. Bé lấy câu mới nhé.");
        }
        if (tokenCodec.isUsed(decoded.tokenId(), nowInstant)) {
            throw new InvalidRequestException("Câu hỏi này đã được trả lời rồi. Bé lấy câu mới nhé.");
        }

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
            throw new ConflictException("Dieppie đang ngủ nghỉ mắt rồi, bé chờ hết giờ nghỉ nhé.");
        }

        int expectedAnswer = parseExpectedAnswer(decoded.payload());
        if (suppliedAnswer != expectedAnswer) {
            rateLimiter.recordFailure(profileId);
            return new MedicineOutcome(
                    new WatchPetMedicineAnswerResponse(false, toView(settings, state, now)),
                    "Chưa đúng rồi, bé thử tính lại để mua thuốc cho Dieppie nhé."
            );
        }

        if (!tokenCodec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException("Câu hỏi này đã được trả lời rồi. Bé lấy câu mới nhé.");
        }

        state.healWithMedicine(now);
        ChildPlayState saved = stateRepository.saveAndFlush(state);
        return new MedicineOutcome(
                new WatchPetMedicineAnswerResponse(true, toView(settings, saved, now)),
                "Giỏi quá! Dieppie uống thuốc xong đã khỏe lại rồi."
        );
    }

    private Viewer requireChildViewer() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException("Dieppie canh giờ nghỉ mắt chỉ áp dụng cho hồ sơ trẻ em.");
        }
        return viewer;
    }

    private static int parseExpectedAnswer(String payload) {
        int sep = payload.indexOf(':');
        String raw = sep >= 0 ? payload.substring(0, sep) : payload;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException("Câu hỏi không hợp lệ. Bé lấy câu mới nhé.");
        }
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
