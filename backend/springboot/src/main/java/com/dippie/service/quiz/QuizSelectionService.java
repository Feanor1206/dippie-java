package com.dippie.service.quiz;

import com.dippie.dto.quiz.QuizAnswerRequest;
import com.dippie.dto.quiz.QuizOptionView;
import com.dippie.dto.quiz.QuizSampleQuestionView;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.quiz.ChildQuizSet;
import com.dippie.model.quiz.MathQuestionGenerator;
import com.dippie.model.quiz.QuizAttempt;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizQuestion;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;
import com.dippie.repository.quiz.ChildQuizSetRepository;
import com.dippie.repository.quiz.QuizAttemptRepository;
import com.dippie.repository.quiz.QuizQuestionRepository;
import com.dippie.repository.quiz.QuizSetRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// trung tâm chọn câu hỏi tránh trùng 30 câu gần nhất, trộn thứ tự A-D, ký token 1 lần và chấm + lưu lịch sử trả lời
@Service
public class QuizSelectionService {

    private static final String INVALID_TOKEN_MESSAGE = "Câu hỏi không hợp lệ hoặc đã hết hạn. Bé lấy câu mới nhé.";
    private static final String TOKEN_USED_MESSAGE = "Câu hỏi này đã được trả lời rồi. Bé lấy câu mới nhé.";
    private static final int RECENT_ISSUED_BUFFER_SIZE = 8;

    private final BuiltInQuizSeeder seeder;
    private final QuizSetRepository quizSetRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ChildQuizSetRepository childQuizSetRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizTokenCodec tokenCodec;
    private final QuizRateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final MathQuestionGenerator mathGenerator = new MathQuestionGenerator(random);
    private final Map<Long, Deque<String>> recentIssuedByProfile = new ConcurrentHashMap<>();

    public QuizSelectionService(
            BuiltInQuizSeeder seeder,
            QuizSetRepository quizSetRepository,
            QuizQuestionRepository quizQuestionRepository,
            ChildQuizSetRepository childQuizSetRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizTokenCodec tokenCodec,
            QuizRateLimiter rateLimiter,
            Clock clock
    ) {
        this.seeder = seeder;
        this.quizSetRepository = quizSetRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.childQuizSetRepository = childQuizSetRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.tokenCodec = tokenCodec;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // chọn 1 câu hỏi phù hợp cho bé, né 30 câu gần nhất, trộn ngẫu nhiên 4 lựa chọn và đóng gói token 1 lần
    @Transactional(readOnly = true)
    public IssuedQuizChallenge issueForChild(
            ChildProfile profile,
            QuizChallengeContext context,
            String scope,
            Duration ttl,
            boolean harderGrade
    ) {
        seeder.ensureSeeded();
        QuizGrade childGrade = profile.getQuizGrade();
        QuizGrade targetGrade = harderGrade ? childGrade.nextGrade() : childGrade;
        List<QuizSet> activeSets = resolveActiveSetsForChild(profile);

        List<QuizAttempt> recentAttempts = quizAttemptRepository
                .findTop30ByProfileIdOrderByAnsweredAtDescIdDesc(profile.getId());
        List<String> recentAttemptKeys = recentAttempts.stream()
                .map(QuizAttempt::getQuestionKey)
                .toList();
        Set<String> avoidKeys = new LinkedHashSet<>(recentAttemptKeys);
        Deque<String> recentlyIssued = recentIssuedByProfile.get(profile.getId());
        if (recentlyIssued != null) {
            synchronized (recentlyIssued) {
                avoidKeys.addAll(recentlyIssued);
            }
        }

        PickedQuestion picked = pickQuestion(activeSets, childGrade, targetGrade, harderGrade, avoidKeys, recentAttemptKeys);
        ShuffledOptions shuffled = shuffleOptions(picked.correctText(), picked.distractors());

        rememberIssuedKey(profile.getId(), picked.questionKey());

        QuizTokenCodec.IssuedToken issued = tokenCodec.issue(
                context,
                profile.getId(),
                scope,
                ttl,
                new QuizTokenCodec.TokenData(
                        picked.setId(),
                        picked.questionId(),
                        shuffled.correctKey(),
                        picked.grade(),
                        picked.subject(),
                        picked.questionKey(),
                        picked.questionText(),
                        picked.setName(),
                        picked.correctText(),
                        picked.explanation()
                )
        );
        LocalDateTime expiresAtUtc = LocalDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);

        return new IssuedQuizChallenge(
                issued.token(),
                picked.questionText(),
                shuffled.options(),
                picked.subject(),
                picked.grade(),
                picked.setName(),
                expiresAtUtc,
                ttl.toSeconds()
        );
    }

    // chấm lựa chọn A-D bằng token ký HMAC, tiêu hủy token ngay ở lần nộp đầu tiên và ghi vào quiz_attempts
    @Transactional
    public VerifiedAnswer verifyAndRecordAnswer(
            Long profileId,
            QuizChallengeContext expectedContext,
            String expectedScope,
            QuizGrade minimumRequiredGrade,
            QuizAnswerRequest request,
            boolean countRateLimitFailure
    ) {
        String rawOption = request == null ? null : request.option();
        QuizOptionKey chosenOption = QuizOptionKey.require(rawOption);

        String rawToken = request == null ? null : request.token();
        QuizTokenCodec.DecodedToken decoded = tokenCodec.decode(rawToken, expectedContext)
                .orElseThrow(() -> new InvalidRequestException(INVALID_TOKEN_MESSAGE));

        Instant nowInstant = clock.instant();
        if (decoded.profileId() != profileId || !decoded.expiresAt().isAfter(nowInstant)) {
            throw new InvalidRequestException(
                    expectedContext == QuizChallengeContext.LOOT_DROP
                            ? "Hết giờ mở rương rồi! Hẹn bé ở rương báu tiếp theo nhé."
                            : INVALID_TOKEN_MESSAGE
            );
        }
        String normalizedScope = expectedScope == null ? "" : expectedScope;
        if (!normalizedScope.equals(decoded.scope())) {
            throw new InvalidRequestException("Câu hỏi không thuộc mục này. Bé lấy câu mới nhé.");
        }
        if (minimumRequiredGrade != null && decoded.grade().order() < minimumRequiredGrade.order()) {
            throw new InvalidRequestException("Khối lớp của bé đã thay đổi. Bé lấy câu hỏi mới nhé.");
        }

        // khóa token 1 lần cho cả đúng lẫn sai để sau khi hiện đáp án đúng bé không thể nộp lại cùng token
        if (!tokenCodec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), nowInstant)) {
            throw new InvalidRequestException(
                    expectedContext == QuizChallengeContext.LOOT_DROP
                            ? "Rương báu này đã được mở rồi."
                            : TOKEN_USED_MESSAGE
            );
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            String redeemedTokenId = decoded.tokenId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        tokenCodec.release(redeemedTokenId);
                    }
                }
            });
        }

        boolean correct = chosenOption == decoded.correctOption();
        long elapsedMs = Math.max(0L, nowInstant.toEpochMilli() - decoded.issuedAtEpochMs());
        int responseMs = (int) Math.min(3_600_000L, elapsedMs);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        try {
            // tránh lỗi khóa ngoại nếu phụ huynh vừa kịp xóa bộ hoặc câu hỏi trong lúc bé đang trả lời
            Long safeSetId = decoded.setId() != null && quizSetRepository.existsById(decoded.setId())
                    ? decoded.setId()
                    : null;
            Long safeQuestionId = decoded.questionId() != null && quizQuestionRepository.existsById(decoded.questionId())
                    ? decoded.questionId()
                    : null;

            quizAttemptRepository.saveAndFlush(new QuizAttempt(
                    profileId,
                    safeSetId,
                    safeQuestionId,
                    decoded.questionKey(),
                    decoded.questionText(),
                    decoded.setName(),
                    decoded.subject(),
                    decoded.grade(),
                    expectedContext,
                    correct,
                    responseMs,
                    nowUtc
            ));
        } catch (RuntimeException ex) {
            tokenCodec.release(decoded.tokenId());
            throw ex;
        }

        if (!correct && countRateLimitFailure) {
            rateLimiter.recordFailure(profileId);
        }

        String wrongMessage = formatGentleWrongMessage(decoded.correctOption(), decoded.correctText());
        return new VerifiedAnswer(
                correct,
                decoded.correctOption().name(),
                decoded.correctText(),
                decoded.explanation(),
                wrongMessage,
                decoded
        );
    }

    // trả 2 đến 3 câu hỏi mẫu theo lớp và danh sách bộ câu hỏi phụ huynh đang chọn để xem trước ngay trong hộp thoại
    @Transactional(readOnly = true)
    public List<QuizSampleQuestionView> previewSamples(Long userId, QuizGrade grade, List<Long> requestedSetIds) {
        seeder.ensureSeeded();
        QuizGrade safeGrade = grade == null ? QuizGrade.LOP_1 : grade;
        List<QuizSet> sets = resolveAccessibleSets(userId, requestedSetIds);
        if (sets.isEmpty()) {
            sets = quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
        }

        List<QuizSampleQuestionView> samples = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        List<Long> staticSetIds = sets.stream()
                .filter(set -> !set.isDynamicGenerator())
                .map(QuizSet::getId)
                .toList();
        Map<Long, QuizSet> setById = sets.stream().collect(Collectors.toMap(QuizSet::getId, s -> s));

        Map<Long, List<QuizQuestion>> staticBySet = new LinkedHashMap<>();
        if (!staticSetIds.isEmpty()) {
            List<QuizQuestion> exact = quizQuestionRepository.findBySetIdInAndActiveTrueAndGrade(staticSetIds, safeGrade);
            for (QuizQuestion q : exact) {
                staticBySet.computeIfAbsent(q.getSetId(), ignored -> new ArrayList<>()).add(q);
            }
            if (staticBySet.isEmpty()) {
                List<QuizQuestion> any = quizQuestionRepository.findBySetIdInAndActiveTrue(staticSetIds);
                any.sort(Comparator.comparingInt(q -> Math.abs(q.getGrade().order() - safeGrade.order())));
                for (QuizQuestion q : any) {
                    staticBySet.computeIfAbsent(q.getSetId(), ignored -> new ArrayList<>()).add(q);
                }
            }
        }

        // ưu tiên lấy tối đa 1 câu đại diện cho mỗi bộ đang bật (tối đa 3 câu mẫu)
        for (QuizSet set : sets) {
            if (samples.size() >= 3) {
                break;
            }
            if (set.isDynamicGenerator()) {
                MathQuestionGenerator.GeneratedMathQuestion math = mathGenerator.generateAvoiding(safeGrade, usedKeys);
                usedKeys.add(math.questionKey());
                samples.add(toSampleView(
                        math.questionText(),
                        math.correctText(),
                        math.distractors(),
                        math.explanation(),
                        QuizSubject.TOAN,
                        math.grade(),
                        set.getName()
                ));
            } else {
                List<QuizQuestion> list = staticBySet.getOrDefault(set.getId(), List.of());
                if (!list.isEmpty()) {
                    QuizQuestion chosen = list.get(random.nextInt(list.size()));
                    usedKeys.add("q:" + chosen.getId());
                    samples.add(toSampleView(chosen, set.getName()));
                }
            }
        }

        // nếu mới có ít hơn 3 câu mẫu thì lấy thêm từ các câu còn lại của các bộ đang chọn
        if (samples.size() < 3) {
            List<QuizQuestion> extras = new ArrayList<>();
            staticBySet.values().forEach(extras::addAll);
            Collections.shuffle(extras, random);
            for (QuizQuestion q : extras) {
                if (samples.size() >= 3) {
                    break;
                }
                if (usedKeys.add("q:" + q.getId())) {
                    QuizSet ownerSet = setById.get(q.getSetId());
                    samples.add(toSampleView(q, ownerSet == null ? "Bộ câu hỏi" : ownerSet.getName()));
                }
            }
        }

        QuizSet dynamicMath = sets.stream().filter(QuizSet::isDynamicGenerator).findFirst().orElse(null);
        while (samples.size() < 3 && dynamicMath != null) {
            MathQuestionGenerator.GeneratedMathQuestion math = mathGenerator.generateAvoiding(safeGrade, usedKeys);
            usedKeys.add(math.questionKey());
            samples.add(toSampleView(
                    math.questionText(),
                    math.correctText(),
                    math.distractors(),
                    math.explanation(),
                    QuizSubject.TOAN,
                    math.grade(),
                    dynamicMath.getName()
            ));
        }

        return List.copyOf(samples);
    }

    // trả danh sách bộ câu hỏi đang hiệu lực của 1 bé (chưa cấu hình thì dùng mặc định các bộ có sẵn)
    public List<QuizSet> resolveActiveSetsForChild(ChildProfile profile) {
        seeder.ensureSeeded();
        List<ChildQuizSet> links = childQuizSetRepository.findByProfileIdOrderBySetIdAsc(profile.getId());
        if (!links.isEmpty()) {
            List<Long> ids = links.stream().map(ChildQuizSet::getSetId).toList();
            Map<Long, QuizSet> byId = quizSetRepository.findByIdIn(ids).stream()
                    .filter(QuizSet::isActive)
                    .filter(set -> set.isBuiltIn() || Objects.equals(set.getOwnerUserId(), profile.getUserId()))
                    .collect(Collectors.toMap(QuizSet::getId, s -> s));
            List<QuizSet> resolved = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        return quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
    }

    public void resetRecentIssued() {
        recentIssuedByProfile.clear();
    }

    public static String formatGentleWrongMessage(QuizOptionKey correctOption, String correctText) {
        return "Gần đúng rồi! Đáp án là " + correctOption.name() + ": " + correctText + " nè";
    }

    private List<QuizSet> resolveAccessibleSets(Long userId, List<Long> requestedSetIds) {
        if (requestedSetIds == null || requestedSetIds.isEmpty()) {
            return quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
        }
        Set<Long> uniqueIds = requestedSetIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc();
        }
        Map<Long, QuizSet> found = quizSetRepository.findByIdIn(uniqueIds).stream()
                .filter(set -> set.isBuiltIn() || Objects.equals(set.getOwnerUserId(), userId))
                .collect(Collectors.toMap(QuizSet::getId, s -> s));
        return uniqueIds.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private PickedQuestion pickQuestion(
            List<QuizSet> activeSets,
            QuizGrade childGrade,
            QuizGrade targetGrade,
            boolean harderGrade,
            Set<String> avoidKeys,
            List<String> recentAttemptKeys
    ) {
        QuizSet dynamicMathSet = activeSets.stream()
                .filter(QuizSet::isDynamicGenerator)
                .findFirst()
                .orElse(null);
        List<QuizSet> staticSets = activeSets.stream()
                .filter(set -> !set.isDynamicGenerator())
                .toList();
        List<Long> staticSetIds = staticSets.stream().map(QuizSet::getId).toList();
        Map<Long, QuizSet> setById = staticSets.stream().collect(Collectors.toMap(QuizSet::getId, s -> s));

        // bước 1: tìm câu hỏi khớp đúng khối lớp mục tiêu trong các bộ đang bật
        List<QuizQuestion> exactStatic = staticSetIds.isEmpty()
                ? List.of()
                : quizQuestionRepository.findBySetIdInAndActiveTrueAndGrade(staticSetIds, targetGrade);

        PickedQuestion chosen = pickFromBuckets(exactStatic, setById, dynamicMathSet, targetGrade, avoidKeys, recentAttemptKeys);
        if (chosen != null) {
            return chosen;
        }

        // bước 2: nếu các bộ tĩnh đang bật không có câu ở đúng targetGrade thì mở rộng sang lớp gần nhất (VIP ưu tiên lớp cao nhất sẵn có)
        if (!staticSetIds.isEmpty()) {
            List<QuizQuestion> allStatic = quizQuestionRepository.findBySetIdInAndActiveTrue(staticSetIds);
            if (!allStatic.isEmpty()) {
                List<QuizQuestion> fallbackStatic;
                if (harderGrade) {
                    int maxAvailableOrder = allStatic.stream()
                            .mapToInt(q -> q.getGrade().order())
                            .max()
                            .orElse(childGrade.order());
                    fallbackStatic = allStatic.stream()
                            .filter(q -> q.getGrade().order() == maxAvailableOrder)
                            .toList();
                } else {
                    int minDistance = allStatic.stream()
                            .mapToInt(q -> Math.abs(q.getGrade().order() - targetGrade.order()))
                            .min()
                            .orElse(0);
                    fallbackStatic = allStatic.stream()
                            .filter(q -> Math.abs(q.getGrade().order() - targetGrade.order()) == minDistance)
                            .toList();
                }
                chosen = pickFromBuckets(fallbackStatic, setById, dynamicMathSet, targetGrade, avoidKeys, recentAttemptKeys);
                if (chosen != null) {
                    return chosen;
                }
            }
        }

        // bước 3: phương án dự phòng cuối cùng là sinh toán tự động ở targetGrade để bé không bao giờ bị kẹt khóa
        QuizSet builtInMath = quizSetRepository.findBySystemKey(QuizSet.SYSTEM_KEY_MATH).orElse(null);
        MathQuestionGenerator.GeneratedMathQuestion math = mathGenerator.generateAvoiding(targetGrade, avoidKeys);
        return fromMath(math, builtInMath);
    }

    private PickedQuestion pickFromBuckets(
            List<QuizQuestion> staticQuestions,
            Map<Long, QuizSet> setById,
            QuizSet dynamicMathSet,
            QuizGrade mathGrade,
            Set<String> avoidKeys,
            List<String> recentAttemptKeys
    ) {
        Map<Long, List<QuizQuestion>> bySet = new LinkedHashMap<>();
        for (QuizQuestion q : staticQuestions) {
            if (setById.containsKey(q.getSetId())) {
                bySet.computeIfAbsent(q.getSetId(), ignored -> new ArrayList<>()).add(q);
            }
        }

        // ưu tiên các bộ còn câu chưa nằm trong 30 câu gần nhất
        List<Long> freshSetIds = new ArrayList<>();
        Map<Long, List<QuizQuestion>> freshBySet = new HashMap<>();
        for (Map.Entry<Long, List<QuizQuestion>> entry : bySet.entrySet()) {
            List<QuizQuestion> fresh = entry.getValue().stream()
                    .filter(q -> !avoidKeys.contains("q:" + q.getId()))
                    .toList();
            if (!fresh.isEmpty()) {
                freshSetIds.add(entry.getKey());
                freshBySet.put(entry.getKey(), fresh);
            }
        }

        int totalFreshSources = freshSetIds.size() + (dynamicMathSet != null ? 1 : 0);
        if (totalFreshSources > 0) {
            int slot = random.nextInt(totalFreshSources);
            if (dynamicMathSet != null && slot == freshSetIds.size()) {
                MathQuestionGenerator.GeneratedMathQuestion math = mathGenerator.generateAvoiding(mathGrade, avoidKeys);
                return fromMath(math, dynamicMathSet);
            }
            Long chosenSetId = freshSetIds.get(slot);
            List<QuizQuestion> pool = freshBySet.get(chosenSetId);
            QuizQuestion picked = pool.get(random.nextInt(pool.size()));
            return fromStatic(picked, setById.get(chosenSetId));
        }

        if (staticQuestions.isEmpty()) {
            return null;
        }

        // nếu kho nhỏ hơn 30 câu và bé đã làm hết tất cả trong 30 lượt gần nhất thì chọn câu cũ nhất (ít lặp gần nhất)
        Map<String, Integer> recencyRank = new HashMap<>();
        for (int i = 0; i < recentAttemptKeys.size(); i++) {
            recencyRank.putIfAbsent(recentAttemptKeys.get(i), i);
        }
        int bestRank = staticQuestions.stream()
                .mapToInt(q -> recencyRank.getOrDefault("q:" + q.getId(), Integer.MAX_VALUE))
                .max()
                .orElse(Integer.MAX_VALUE);
        List<QuizQuestion> oldest = staticQuestions.stream()
                .filter(q -> recencyRank.getOrDefault("q:" + q.getId(), Integer.MAX_VALUE) == bestRank)
                .toList();
        QuizQuestion picked = oldest.get(random.nextInt(oldest.size()));
        return fromStatic(picked, setById.get(picked.getSetId()));
    }

    private static PickedQuestion fromStatic(QuizQuestion question, QuizSet set) {
        String correctText = question.optionText(question.getCorrectOption());
        List<String> distractors = new ArrayList<>(3);
        for (QuizOptionKey key : QuizOptionKey.values()) {
            if (key != question.getCorrectOption()) {
                distractors.add(question.optionText(key));
            }
        }
        return new PickedQuestion(
                set == null ? question.getSetId() : set.getId(),
                question.getId(),
                "q:" + question.getId(),
                question.getQuestionText(),
                correctText,
                distractors,
                question.getExplanation(),
                question.getSubject(),
                question.getGrade(),
                set == null ? "Bộ câu hỏi" : set.getName()
        );
    }

    private static PickedQuestion fromMath(MathQuestionGenerator.GeneratedMathQuestion math, QuizSet mathSet) {
        return new PickedQuestion(
                mathSet == null ? null : mathSet.getId(),
                null,
                math.questionKey(),
                math.questionText(),
                math.correctText(),
                math.distractors(),
                math.explanation(),
                QuizSubject.TOAN,
                math.grade(),
                mathSet == null ? "Toán học" : mathSet.getName()
        );
    }

    private ShuffledOptions shuffleOptions(String correctText, List<String> distractors) {
        List<OptionCandidate> items = new ArrayList<>(4);
        items.add(new OptionCandidate(correctText, true));
        for (String distractor : distractors) {
            items.add(new OptionCandidate(distractor, false));
        }
        Collections.shuffle(items, random);
        List<QuizOptionView> views = new ArrayList<>(4);
        QuizOptionKey correctKey = QuizOptionKey.A;
        QuizOptionKey[] keys = QuizOptionKey.values();
        for (int i = 0; i < 4; i++) {
            OptionCandidate item = items.get(i);
            views.add(new QuizOptionView(keys[i].name(), item.text()));
            if (item.correct()) {
                correctKey = keys[i];
            }
        }
        return new ShuffledOptions(List.copyOf(views), correctKey);
    }

    private QuizSampleQuestionView toSampleView(QuizQuestion q, String setName) {
        String correctText = q.optionText(q.getCorrectOption());
        List<String> distractors = new ArrayList<>(3);
        for (QuizOptionKey key : QuizOptionKey.values()) {
            if (key != q.getCorrectOption()) {
                distractors.add(q.optionText(key));
            }
        }
        return toSampleView(q.getQuestionText(), correctText, distractors, q.getExplanation(), q.getSubject(), q.getGrade(), setName);
    }

    private QuizSampleQuestionView toSampleView(
            String questionText,
            String correctText,
            List<String> distractors,
            String explanation,
            QuizSubject subject,
            QuizGrade grade,
            String setName
    ) {
        ShuffledOptions shuffled = shuffleOptions(correctText, distractors);
        return new QuizSampleQuestionView(
                questionText,
                shuffled.options(),
                shuffled.correctKey().name(),
                explanation,
                subject.name(),
                subject.label(),
                grade.name(),
                grade.label(),
                setName
        );
    }

    private void rememberIssuedKey(Long profileId, String questionKey) {
        Deque<String> deque = recentIssuedByProfile.computeIfAbsent(profileId, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.remove(questionKey);
            deque.addFirst(questionKey);
            while (deque.size() > RECENT_ISSUED_BUFFER_SIZE) {
                deque.removeLast();
            }
        }
    }

    private record OptionCandidate(String text, boolean correct) {
    }

    private record ShuffledOptions(List<QuizOptionView> options, QuizOptionKey correctKey) {
    }

    private record PickedQuestion(
            Long setId,
            Long questionId,
            String questionKey,
            String questionText,
            String correctText,
            List<String> distractors,
            String explanation,
            QuizSubject subject,
            QuizGrade grade,
            String setName
    ) {
    }

    public record IssuedQuizChallenge(
            String token,
            String question,
            List<QuizOptionView> options,
            QuizSubject subject,
            QuizGrade grade,
            String setName,
            LocalDateTime expiresAt,
            long expiresInSeconds
    ) {
    }

    public record VerifiedAnswer(
            boolean correct,
            String correctOption,
            String correctText,
            String explanation,
            String gentleWrongMessage,
            QuizTokenCodec.DecodedToken decoded
    ) {
    }
}
