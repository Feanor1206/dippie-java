package com.dippie.service.quiz;

import com.dippie.dto.quiz.ChildQuizConfigView;
import com.dippie.dto.quiz.ChildQuizSetRef;
import com.dippie.dto.quiz.ChildQuizWeeklySummaryView;
import com.dippie.dto.quiz.ConfirmGradeChangeRequest;
import com.dippie.dto.quiz.GradeSuggestionView;
import com.dippie.dto.quiz.HardQuestionStatView;
import com.dippie.dto.quiz.QuizSetWeeklyStatView;
import com.dippie.dto.quiz.UpdateChildQuizSettingsRequest;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.quiz.ChildQuizSet;
import com.dippie.model.quiz.QuizAttempt;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.user.User;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.quiz.ChildQuizSetRepository;
import com.dippie.repository.quiz.QuizAttemptRepository;
import com.dippie.repository.quiz.QuizSetRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ParentModeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// quản lý cấu hình lớp và bộ câu hỏi của từng bé, thống kê tuần và gợi ý nâng/hạ lớp cần phụ huynh xác nhận PIN
@Service
public class QuizInsightService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SUMMARY_DAYS = 7;
    private static final int MIN_UP_QUESTIONS = 20;
    private static final int UP_THRESHOLD_PERCENT = 90;
    private static final int MIN_DOWN_QUESTIONS = 10;
    private static final int DOWN_THRESHOLD_PERCENT = 50;
    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh cài đặt câu hỏi.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ trẻ em này.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ParentModeService parentModeService;
    private final BuiltInQuizSeeder seeder;
    private final QuizBankService quizBankService;
    private final QuizSetRepository quizSetRepository;
    private final ChildQuizSetRepository childQuizSetRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final Clock clock;

    public QuizInsightService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ParentModeService parentModeService,
            BuiltInQuizSeeder seeder,
            QuizBankService quizBankService,
            QuizSetRepository quizSetRepository,
            ChildQuizSetRepository childQuizSetRepository,
            QuizAttemptRepository quizAttemptRepository,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.parentModeService = parentModeService;
        this.seeder = seeder;
        this.quizBankService = quizBankService;
        this.quizSetRepository = quizSetRepository;
        this.childQuizSetRepository = childQuizSetRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.clock = clock;
    }

    // trả cấu hình câu hỏi (lớp + bộ đang bật) và thống kê 7 ngày gần nhất cho từng bé trong nhà
    @Transactional(readOnly = true)
    public List<ChildQuizConfigView> listParentChildConfigs() {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            return List.of();
        }
        seeder.ensureSeeded();
        List<ChildProfile> profiles = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId());
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<QuizSet> builtInActive = quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
        List<QuizSet> ownedSets = quizSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(user.getId());
        Map<Long, QuizSet> accessibleById = new LinkedHashMap<>();
        for (QuizSet s : builtInActive) {
            accessibleById.put(s.getId(), s);
        }
        for (QuizSet s : ownedSets) {
            accessibleById.put(s.getId(), s);
        }

        List<Long> profileIds = profiles.stream().map(ChildProfile::getId).toList();
        Map<Long, List<Long>> linkedSetIdsByProfile = new HashMap<>();
        for (ChildQuizSet link : childQuizSetRepository.findByProfileIdIn(profileIds)) {
            linkedSetIdsByProfile.computeIfAbsent(link.getProfileId(), ignored -> new ArrayList<>()).add(link.getSetId());
        }

        LocalDateTime sinceUtc = weekWindowStartUtc();
        Map<Long, List<QuizAttempt>> attemptsByProfile = new HashMap<>();
        for (QuizAttempt attempt : quizAttemptRepository
                .findByProfileIdInAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDescIdDesc(profileIds, sinceUtc)) {
            attemptsByProfile.computeIfAbsent(attempt.getProfileId(), ignored -> new ArrayList<>()).add(attempt);
        }

        return profiles.stream()
                .map(profile -> buildChildConfigView(
                        profile,
                        linkedSetIdsByProfile.getOrDefault(profile.getId(), List.of()),
                        accessibleById,
                        builtInActive,
                        attemptsByProfile.getOrDefault(profile.getId(), List.of())
                ))
                .toList();
    }

    // phụ huynh cập nhật lớp học và danh sách bộ câu hỏi đang bật cho 1 bé (bắt buộc đúng PIN)
    @Transactional
    public ChildQuizConfigView updateChildQuizSettings(Long profileId, UpdateChildQuizSettingsRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));

        QuizGrade nextGrade = request != null && request.grade() != null && !request.grade().isBlank()
                ? QuizGrade.require(request.grade())
                : profile.getQuizGrade();
        List<Long> validSetIds = quizBankService.validateAssignableSetIds(
                user.getId(),
                request == null ? null : request.activeSetIds()
        );

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        profile.updateQuizGrade(nextGrade, now);
        childProfileRepository.saveAndFlush(profile);
        quizBankService.assignSetsForChild(user.getId(), profile.getId(), validSetIds, true);

        return loadSingleChildConfig(user.getId(), profile);
    }

    // phụ huynh xác nhận bằng PIN để đổi lớp theo gợi ý từ báo cáo tuần (không bao giờ tự đổi ngầm)
    @Transactional
    public ChildQuizConfigView confirmGradeChange(Long profileId, ConfirmGradeChangeRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));

        QuizGrade nextGrade = QuizGrade.require(request == null ? null : request.grade());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        profile.updateQuizGrade(nextGrade, now);
        childProfileRepository.saveAndFlush(profile);

        return loadSingleChildConfig(user.getId(), profile);
    }

    // tính gợi ý tăng/giảm lớp: đúng > 90% qua ít nhất 20 câu gợi ý lên lớp, đúng < 50% qua ít nhất 10 câu gợi ý hạ lớp
    public static GradeSuggestionView evaluateGradeSuggestion(QuizGrade currentGrade, int totalAnswered, int correctCount) {
        if (currentGrade == null || totalAnswered <= 0) {
            return null;
        }
        int accuracyPercent = (int) Math.round((correctCount * 100.0) / totalAnswered);
        if (totalAnswered >= MIN_UP_QUESTIONS
                && (long) correctCount * 100L > (long) totalAnswered * UP_THRESHOLD_PERCENT
                && currentGrade.hasNext()) {
            QuizGrade next = currentGrade.nextGrade();
            String message = "Bé làm rất tốt (" + accuracyPercent + "% đúng qua " + totalAnswered
                    + " câu tuần này)! Ba mẹ có muốn nâng lên " + next.label() + " không?";
            return new GradeSuggestionView(
                    "UP",
                    currentGrade.name(),
                    currentGrade.label(),
                    next.name(),
                    next.label(),
                    totalAnswered,
                    accuracyPercent,
                    message
            );
        }
        if (totalAnswered >= MIN_DOWN_QUESTIONS
                && (long) correctCount * 100L < (long) totalAnswered * DOWN_THRESHOLD_PERCENT
                && currentGrade.hasPrevious()) {
            QuizGrade prev = currentGrade.previousGrade();
            String message = "Tuần này bé mới đúng " + accuracyPercent + "% (" + totalAnswered
                    + " câu), có vẻ hơi khó. Ba mẹ có muốn hạ xuống " + prev.label() + " để bé tự tin hơn không?";
            return new GradeSuggestionView(
                    "DOWN",
                    currentGrade.name(),
                    currentGrade.label(),
                    prev.name(),
                    prev.label(),
                    totalAnswered,
                    accuracyPercent,
                    message
            );
        }
        return null;
    }

    private ChildQuizConfigView loadSingleChildConfig(Long userId, ChildProfile profile) {
        seeder.ensureSeeded();
        List<QuizSet> builtInActive = quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
        List<QuizSet> ownedSets = quizSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(userId);
        Map<Long, QuizSet> accessibleById = new LinkedHashMap<>();
        for (QuizSet s : builtInActive) {
            accessibleById.put(s.getId(), s);
        }
        for (QuizSet s : ownedSets) {
            accessibleById.put(s.getId(), s);
        }
        List<Long> linkedIds = childQuizSetRepository.findByProfileIdOrderBySetIdAsc(profile.getId())
                .stream()
                .map(ChildQuizSet::getSetId)
                .toList();
        List<QuizAttempt> weeklyAttempts = quizAttemptRepository
                .findByProfileIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDescIdDesc(profile.getId(), weekWindowStartUtc());
        return buildChildConfigView(profile, linkedIds, accessibleById, builtInActive, weeklyAttempts);
    }

    private ChildQuizConfigView buildChildConfigView(
            ChildProfile profile,
            List<Long> linkedSetIds,
            Map<Long, QuizSet> accessibleById,
            List<QuizSet> builtInActive,
            List<QuizAttempt> weeklyAttempts
    ) {
        List<QuizSet> effectiveSets = linkedSetIds.stream()
                .map(accessibleById::get)
                .filter(Objects::nonNull)
                .filter(QuizSet::isActive)
                .toList();
        if (effectiveSets.isEmpty()) {
            effectiveSets = builtInActive;
        }
        List<ChildQuizSetRef> setRefs = effectiveSets.stream()
                .map(s -> new ChildQuizSetRef(s.getId(), s.getName(), s.isBuiltIn(), s.isDynamicGenerator(), s.isActive()))
                .toList();
        List<Long> activeIds = effectiveSets.stream().map(QuizSet::getId).toList();
        QuizGrade grade = profile.getQuizGrade();
        QuizGrade vipGrade = grade.nextGrade();
        ChildQuizWeeklySummaryView summary = summarizeWeeklyAttempts(grade, weeklyAttempts);

        return new ChildQuizConfigView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                grade.name(),
                grade.label(),
                vipGrade.name(),
                vipGrade.label(),
                activeIds,
                setRefs,
                summary
        );
    }

    private ChildQuizWeeklySummaryView summarizeWeeklyAttempts(QuizGrade currentGrade, List<QuizAttempt> attempts) {
        int total = attempts.size();
        int correct = 0;
        long totalResponseMs = 0L;

        Map<String, SetAccumulator> bySetMap = new LinkedHashMap<>();
        Map<String, QuestionAccumulator> byQuestionMap = new LinkedHashMap<>();

        for (QuizAttempt attempt : attempts) {
            if (attempt.isCorrect()) {
                correct++;
            }
            totalResponseMs += Math.max(0, attempt.getResponseMs());

            String setGroupKey = attempt.getSetId() != null
                    ? "id:" + attempt.getSetId()
                    : "name:" + attempt.getSetName();
            SetAccumulator setAcc = bySetMap.computeIfAbsent(
                    setGroupKey,
                    ignored -> new SetAccumulator(attempt.getSetId(), attempt.getSetName())
            );
            setAcc.record(attempt.isCorrect());

            String qGroupKey = attempt.getQuestionKey() + "|" + attempt.getSetName();
            QuestionAccumulator qAcc = byQuestionMap.computeIfAbsent(
                    qGroupKey,
                    ignored -> new QuestionAccumulator(
                            attempt.getQuestionText(),
                            attempt.getSetName(),
                            attempt.getSubject().label(),
                            attempt.getGrade().label()
                    )
            );
            qAcc.record(attempt.isCorrect());
        }

        int accuracyPercent = total == 0 ? 0 : (int) Math.round((correct * 100.0) / total);
        int avgResponseMs = total == 0 ? 0 : (int) Math.round(totalResponseMs / (double) total);

        List<QuizSetWeeklyStatView> bySet = bySetMap.values().stream()
                .map(SetAccumulator::toView)
                .sorted(Comparator.comparingInt(QuizSetWeeklyStatView::answered).reversed())
                .toList();

        List<HardQuestionStatView> hardest = byQuestionMap.values().stream()
                .filter(acc -> acc.wrongCount > 0)
                .map(QuestionAccumulator::toView)
                .sorted(Comparator.comparingInt(HardQuestionStatView::accuracyPercent)
                        .thenComparing(Comparator.comparingInt(HardQuestionStatView::wrongCount).reversed())
                        .thenComparing(Comparator.comparingInt(HardQuestionStatView::attempts).reversed()))
                .limit(5)
                .toList();

        GradeSuggestionView suggestion = evaluateGradeSuggestion(currentGrade, total, correct);

        return new ChildQuizWeeklySummaryView(
                SUMMARY_DAYS,
                total,
                correct,
                accuracyPercent,
                avgResponseMs,
                bySet,
                hardest,
                suggestion
        );
    }

    private LocalDateTime weekWindowStartUtc() {
        LocalDate todayVietnam = ScreenTimeSummary.todayVietnam(clock);
        LocalDate startVietnam = todayVietnam.minusDays(SUMMARY_DAYS - 1L);
        return LocalDateTime.ofInstant(
                startVietnam.atStartOfDay(VIETNAM_ZONE).toInstant(),
                ZoneOffset.UTC
        );
    }

    private User requireParentModeAndPin(Long userId, String pin) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, pin);
        return user;
    }

    private static final class SetAccumulator {
        private final Long setId;
        private final String setName;
        private int answered;
        private int correct;

        SetAccumulator(Long setId, String setName) {
            this.setId = setId;
            this.setName = setName;
        }

        void record(boolean isCorrect) {
            answered++;
            if (isCorrect) {
                correct++;
            }
        }

        QuizSetWeeklyStatView toView() {
            int pct = answered == 0 ? 0 : (int) Math.round((correct * 100.0) / answered);
            return new QuizSetWeeklyStatView(setId, setName, answered, correct, pct);
        }
    }

    private static final class QuestionAccumulator {
        private final String questionText;
        private final String setName;
        private final String subjectLabel;
        private final String gradeLabel;
        private int attempts;
        private int wrongCount;

        QuestionAccumulator(String questionText, String setName, String subjectLabel, String gradeLabel) {
            this.questionText = questionText;
            this.setName = setName;
            this.subjectLabel = subjectLabel;
            this.gradeLabel = gradeLabel;
        }

        void record(boolean isCorrect) {
            attempts++;
            if (!isCorrect) {
                wrongCount++;
            }
        }

        HardQuestionStatView toView() {
            int correct = attempts - wrongCount;
            int pct = attempts == 0 ? 0 : (int) Math.round((correct * 100.0) / attempts);
            return new HardQuestionStatView(questionText, setName, subjectLabel, gradeLabel, attempts, wrongCount, pct);
        }
    }
}
