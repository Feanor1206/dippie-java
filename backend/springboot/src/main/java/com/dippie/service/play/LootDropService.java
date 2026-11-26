package com.dippie.service.play;

import com.dippie.dto.play.LootDropAnswerResponse;
import com.dippie.dto.play.LootDropMissRequest;
import com.dippie.dto.play.LootDropOpenRequest;
import com.dippie.dto.play.LootDropOpenResponse;
import com.dippie.dto.play.LootDropSpawnResponse;
import com.dippie.dto.play.LootStatusView;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.play.ChildPlaySettings;
import com.dippie.model.play.ChildPlayState;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ScreenTimeService;
import com.dippie.service.quiz.QuizSelectionService;
import com.dippie.service.quiz.QuizTokenCodec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// quản lý rương báu bay ngang: phát sau 5 đến 7 phút xem, hỏi trắc nghiệm nhanh 10 giây, tính combo và cộng dòng LOOT
@Service
public class LootDropService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String LOOT_DISABLED = "Tính năng rương báu bay ngang đang tắt.";
    private static final int BASE_REWARD_MINUTES = 3;
    private static final int VISIBLE_SECONDS = 15;
    private static final int DEFAULT_QUESTION_TIMER_SECONDS = 12;

    // bé nhỏ tuổi có thêm thời gian đọc hoặc nghe câu hỏi rương báu
    public static int timerSecondsForGrade(QuizGrade grade) {
        if (grade == null) {
            return DEFAULT_QUESTION_TIMER_SECONDS;
        }
        return switch (grade) {
            case MAU_GIAO -> 25;
            case LOP_1 -> 20;
            case LOP_2 -> 15;
            default -> DEFAULT_QUESTION_TIMER_SECONDS;
        };
    }

    private final CurrentViewer currentViewer;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository settingsRepository;
    private final ChildPlayStateRepository stateRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final ScreenTimeService screenTimeService;
    private final PlayPlaybackGuard playbackGuard;
    private final PlayTokenCodec tokenCodec;
    private final QuizTokenCodec quizTokenCodec;
    private final QuizSelectionService quizSelectionService;
    private final Clock clock;

    public LootDropService(
            CurrentViewer currentViewer,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository settingsRepository,
            ChildPlayStateRepository stateRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            ScreenTimeService screenTimeService,
            PlayPlaybackGuard playbackGuard,
            PlayTokenCodec tokenCodec,
            QuizTokenCodec quizTokenCodec,
            QuizSelectionService quizSelectionService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.childProfileRepository = childProfileRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.ledgerRepository = ledgerRepository;
        this.screenTimeService = screenTimeService;
        this.playbackGuard = playbackGuard;
        this.tokenCodec = tokenCodec;
        this.quizTokenCodec = quizTokenCodec;
        this.quizSelectionService = quizSelectionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LootStatusView buildStatus(Long profileId, ChildPlaySettings settings, LocalDateTime now) {
        if (!settings.isLootDropEnabled()) {
            return LootStatusView.disabled();
        }
        ChildPlayState state = stateRepository.findById(profileId).orElse(null);
        if (state == null) {
            return new LootStatusView(true, false, 0);
        }
        boolean petBlocked = playbackGuard.isWatchPetBlocking(profileId, now);
        return new LootStatusView(true, !petBlocked && state.isLootReady(), state.getLootCombo());
    }

    // phát rương báu có chữ ký và hạn 18s (15s bay + 3s trễ mạng) chỉ khi bé đã xem đủ ngưỡng 5 đến 7 phút từ lần trước
    @Transactional
    public LootDropSpawnResponse spawnDrop() {
        Viewer viewer = requireChildViewer();
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        if (!settings.isLootDropEnabled()) {
            throw new InvalidRequestException(LOOT_DISABLED);
        }
        // hết giờ xem hoặc Dieppie đang khóa thì không phát rương
        screenTimeService.requireUnlockedForProfile(viewer.userId(), profile.getId());

        ChildPlayState state = stateRepository.findById(profile.getId())
                .orElseGet(() -> new ChildPlayState(profile.getId(), playbackGuard.randomLootThreshold(), now));
        if (!state.isLootReady()) {
            throw new ConflictException("Bé xem thêm một chút nữa rương báu mới bay tới nhé.");
        }

        state.markLootSpawned(playbackGuard.randomLootThreshold(), now);
        stateRepository.saveAndFlush(state);

        // cộng thêm 3 giây đệm mạng bên cạnh 15 giây hiển thị trên màn hình
        PlayTokenCodec.IssuedToken issued = tokenCodec.issue(
                PlayTokenCodec.KIND_LOOT_DROP,
                profile.getId(),
                Duration.ofSeconds(VISIBLE_SECONDS + 3),
                "drop"
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        return new LootDropSpawnResponse(issued.token(), VISIBLE_SECONDS, expiresAtUtc, state.getLootCombo());
    }

    // bé bấm vào rương đang bay: đổi dropToken 1 lần lấy câu hỏi trắc nghiệm nhanh hạn 10 giây (+3s đệm mạng)
    @Transactional
    public LootDropOpenResponse openDrop(LootDropOpenRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();
        String rawDropToken = request == null ? null : request.dropToken();
        PlayTokenCodec.DecodedToken decoded = tokenCodec.decode(rawDropToken, PlayTokenCodec.KIND_LOOT_DROP)
                .orElseThrow(() -> new InvalidRequestException("Rương báu đã bay mất rồi, bé chờ rương sau nhé."));
        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId || !decoded.expiresAt().isAfter(nowInstant)) {
            throw new InvalidRequestException("Rương báu đã hết hạn rồi, bé chờ rương sau nhé.");
        }
        if (!tokenCodec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException("Rương báu này đã được mở rồi.");
        }

        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        if (!settings.isLootDropEnabled()) {
            throw new InvalidRequestException(LOOT_DISABLED);
        }
        ChildPlayState state = stateRepository.findById(profile.getId()).orElse(null);
        int currentCombo = state == null ? 0 : state.getLootCombo();

        int timerSeconds = timerSecondsForGrade(profile.getQuizGrade());
        QuizSelectionService.IssuedQuizChallenge issued = quizSelectionService.issueForChild(
                profile,
                QuizChallengeContext.LOOT_DROP,
                "",
                Duration.ofSeconds(timerSeconds + 5),
                false
        );
        int potentialComboBonus = ((currentCombo + 1) % 3 == 0) ? 1 : 0;
        return new LootDropOpenResponse(
                issued.token(),
                issued.question(),
                issued.options(),
                issued.subject().name(),
                issued.subject().label(),
                issued.grade().name(),
                issued.grade().label(),
                issued.setName(),
                timerSeconds,
                issued.expiresAt(),
                currentCombo,
                BASE_REWARD_MINUTES,
                potentialComboBonus
        );
    }

    // chấm lựa chọn rương báu: đúng cộng +3 phút (combo chia hết cho 3 thêm +1 phút) vào dòng LOOT theo trần ngày, sai về combo 0
    @Transactional
    public LootAnswerOutcome answerDrop(QuizAnswerRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();

        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlayState state = stateRepository.findById(profile.getId())
                .orElseGet(() -> new ChildPlayState(profile.getId(), playbackGuard.randomLootThreshold(), now));

        QuizSelectionService.VerifiedAnswer verified = quizSelectionService.verifyAndRecordAnswer(
                profileId,
                QuizChallengeContext.LOOT_DROP,
                "",
                null,
                request,
                false
        );

        if (!verified.correct()) {
            state.recordLootMissOrWrong(now);
            stateRepository.saveAndFlush(state);
            ScreenTimeSummary current = screenTimeService.summarize(profile, today);
            LootDropAnswerResponse response = new LootDropAnswerResponse(
                    false,
                    verified.correctOption(),
                    verified.correctText(),
                    verified.explanation(),
                    0,
                    0,
                    0,
                    0,
                    current.bonusCapReached(),
                    ScreenTimeStatusView.from(current)
            );
            return new LootAnswerOutcome(response, verified.gentleWrongMessage());
        }

        int newCombo = state.recordLootCorrect(now);
        stateRepository.saveAndFlush(state);
        int comboBonusMinutes = (newCombo % 3 == 0) ? 1 : 0;
        int rawSeconds = (BASE_REWARD_MINUTES + comboBonusMinutes) * 60;

        ScreenTimeSummary before = screenTimeService.summarize(profile, today);
        int creditedSeconds = (int) Math.min(rawSeconds, before.bonusRemainingSeconds());
        if (creditedSeconds > 0) {
            ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                    profile.getId(),
                    today,
                    ScreenTimeLedgerKind.LOOT,
                    creditedSeconds,
                    now
            ));
        }

        ScreenTimeSummary after = screenTimeService.summarize(profile, today);
        int earnedMinutes = creditedSeconds / 60;
        LootDropAnswerResponse response = new LootDropAnswerResponse(
                true,
                verified.correctOption(),
                verified.correctText(),
                verified.explanation(),
                creditedSeconds,
                earnedMinutes,
                newCombo,
                comboBonusMinutes,
                after.bonusCapReached(),
                ScreenTimeStatusView.from(after)
        );
        String message;
        if (creditedSeconds <= 0) {
            message = "Đúng rồi! Nhưng hôm nay bé đã nhận đủ tổng phút thưởng rồi nhé.";
        } else if (comboBonusMinutes > 0) {
            message = "Tuyệt vời! Combo " + newCombo + " giúp bé nhận +" + earnedMinutes + " phút xem phim!";
        } else {
            message = "Chúc mừng! Bé mở rương nhận thêm " + earnedMinutes + " phút xem phim.";
        }
        return new LootAnswerOutcome(response, message);
    }

    // khi bé để rương trôi qua hoặc hết 10 giây không kịp trả lời thì đặt lại combo về 0
    @Transactional
    public LootStatusView reportMiss(LootDropMissRequest request) {
        Viewer viewer = requireChildViewer();
        Long profileId = viewer.profileId();
        String rawToken = request == null ? null : request.token();
        Long tokenProfileId = tokenCodec.decode(rawToken, PlayTokenCodec.KIND_LOOT_DROP)
                .map(PlayTokenCodec.DecodedToken::profileId)
                .or(() -> quizTokenCodec.decode(rawToken, QuizChallengeContext.LOOT_DROP)
                        .map(QuizTokenCodec.DecodedToken::profileId))
                .orElseThrow(() -> new InvalidRequestException("Mã rương báu không hợp lệ."));
        if (!tokenProfileId.equals(profileId)) {
            throw new InvalidRequestException("Mã rương báu không thuộc hồ sơ này.");
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildPlaySettings settings = settingsRepository.findById(profile.getId())
                .orElseGet(() -> ChildPlaySettings.defaults(profile.getId(), now));
        ChildPlayState state = stateRepository.findById(profile.getId()).orElse(null);
        if (state != null) {
            state.recordLootMissOrWrong(now);
            stateRepository.saveAndFlush(state);
        }
        return new LootStatusView(settings.isLootDropEnabled(), false, 0);
    }

    private Viewer requireChildViewer() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException("Rương báu bay ngang chỉ áp dụng cho hồ sơ trẻ em.");
        }
        return viewer;
    }

    public record LootAnswerOutcome(LootDropAnswerResponse response, String message) {
    }
}
