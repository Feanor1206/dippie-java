package com.dippie.service.quiz;

import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.quiz.CreateQuizSetRequest;
import com.dippie.dto.quiz.QuizCsvImportRequest;
import com.dippie.dto.quiz.QuizCsvImportResponse;
import com.dippie.dto.quiz.QuizCsvPreviewRequest;
import com.dippie.dto.quiz.QuizCsvPreviewResponse;
import com.dippie.dto.quiz.QuizGradeMeta;
import com.dippie.dto.quiz.QuizQuestionMutationRequest;
import com.dippie.dto.quiz.QuizQuestionView;
import com.dippie.dto.quiz.QuizSetDetailResponse;
import com.dippie.dto.quiz.QuizSetView;
import com.dippie.dto.quiz.QuizSetsOverviewResponse;
import com.dippie.dto.quiz.QuizSubjectMeta;
import com.dippie.dto.quiz.UpdateQuizSetRequest;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.quiz.ChildQuizSet;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizQuestion;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;
import com.dippie.model.user.User;
import com.dippie.repository.quiz.ChildQuizSetRepository;
import com.dippie.repository.quiz.QuizAttemptRepository;
import com.dippie.repository.quiz.QuizQuestionRepository;
import com.dippie.repository.quiz.QuizSetRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ParentModeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// quản lý CRUD bộ câu hỏi, thêm sửa xóa câu hỏi thủ công và nhập CSV riêng tư theo từng tài khoản phụ huynh
@Service
public class QuizBankService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể quản lý ngân hàng câu hỏi.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String SET_NOT_FOUND = "Không tìm thấy bộ câu hỏi này.";
    private static final String QUESTION_NOT_FOUND = "Không tìm thấy câu hỏi này.";
    private static final String BUILT_IN_READ_ONLY = "Không thể chỉnh sửa hoặc xóa bộ câu hỏi mặc định của hệ thống.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ParentModeService parentModeService;
    private final BuiltInQuizSeeder seeder;
    private final QuizSetRepository quizSetRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ChildQuizSetRepository childQuizSetRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizCsvParser csvParser;
    private final Clock clock;

    public QuizBankService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ParentModeService parentModeService,
            BuiltInQuizSeeder seeder,
            QuizSetRepository quizSetRepository,
            QuizQuestionRepository quizQuestionRepository,
            ChildQuizSetRepository childQuizSetRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizCsvParser csvParser,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.parentModeService = parentModeService;
        this.seeder = seeder;
        this.quizSetRepository = quizSetRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.childQuizSetRepository = childQuizSetRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.csvParser = csvParser;
        this.clock = clock;
    }

    // liệt kê 3 bộ có sẵn cùng các bộ riêng của tài khoản hiện tại (bộ của nhà khác tuyệt đối không hiện)
    @Transactional(readOnly = true)
    public QuizSetsOverviewResponse listSets() {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        seeder.ensureSeeded();
        List<QuizSet> builtIn = quizSetRepository.findByBuiltInTrueOrderByIdAsc();
        List<QuizSet> owned = quizSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(viewer.userId());
        List<QuizSet> combined = new ArrayList<>(builtIn.size() + owned.size());
        combined.addAll(builtIn);
        combined.addAll(owned);

        Map<Long, Long> counts = countQuestionsBySet(combined);
        List<QuizSetView> views = combined.stream()
                .map(set -> QuizSetView.from(set, counts.getOrDefault(set.getId(), 0L)))
                .toList();

        return new QuizSetsOverviewResponse(
                views,
                owned.size(),
                QuizSet.MAX_PER_ACCOUNT,
                QuizSet.MAX_QUESTIONS_PER_SET,
                QuizSet.MAX_CSV_BYTES,
                Arrays.stream(QuizGrade.values()).map(QuizGradeMeta::from).toList(),
                Arrays.stream(QuizSubject.values()).map(QuizSubjectMeta::from).toList()
        );
    }

    // tạo bộ câu hỏi mới cho tài khoản phụ huynh (cần PIN, trần 20 bộ mỗi tài khoản)
    @Transactional
    public QuizSetView createSet(CreateQuizSetRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        if (quizSetRepository.countByOwnerUserId(user.getId()) >= QuizSet.MAX_PER_ACCOUNT) {
            throw new InvalidRequestException("Mỗi tài khoản chỉ tạo được tối đa 20 bộ câu hỏi.");
        }
        QuizSubject subject = request != null && request.defaultSubject() != null && !request.defaultSubject().isBlank()
                ? QuizSubject.require(request.defaultSubject())
                : QuizSubject.KHAC;
        QuizGrade grade = request != null && request.defaultGrade() != null && !request.defaultGrade().isBlank()
                ? QuizGrade.require(request.defaultGrade())
                : null;
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        QuizSet created = QuizSet.ownedByParent(
                user.getId(),
                request == null ? null : request.name(),
                request == null ? null : request.description(),
                subject,
                grade,
                now
        );
        QuizSet saved = quizSetRepository.saveAndFlush(created);
        return QuizSetView.from(saved, 0L);
    }

    // sửa tên, mô tả, môn/lớp mặc định hoặc tắt/bật bộ câu hỏi của chính tài khoản phụ huynh
    @Transactional
    public QuizSetView updateSet(Long setId, UpdateQuizSetRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());

        QuizSubject subject = request != null && request.defaultSubject() != null && !request.defaultSubject().isBlank()
                ? QuizSubject.require(request.defaultSubject())
                : null;
        QuizGrade grade = request != null && request.defaultGrade() != null && !request.defaultGrade().isBlank()
                ? QuizGrade.require(request.defaultGrade())
                : (request != null && request.defaultGrade() != null ? null : set.getDefaultGrade());

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        set.update(
                request == null ? null : request.name(),
                request == null ? null : request.description(),
                subject,
                grade,
                request == null ? null : request.active(),
                now
        );
        QuizSet saved = quizSetRepository.saveAndFlush(set);
        return QuizSetView.from(saved, quizQuestionRepository.countBySetId(saved.getId()));
    }

    // xóa hẳn bộ câu hỏi của phụ huynh cùng các câu bên trong và liên kết gán cho bé (giữ snapshot báo cáo tuần)
    @Transactional
    public void deleteSet(Long setId, PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());

        quizAttemptRepository.clearReferencesBySetId(set.getId());
        childQuizSetRepository.deleteBySetId(set.getId());
        quizQuestionRepository.deleteBySetId(set.getId());
        quizSetRepository.delete(set);
        quizSetRepository.flush();
    }

    // xem chi tiết câu hỏi trong 1 bộ (bộ có sẵn hoặc bộ riêng của chính tài khoản, nhà khác nhận 404)
    @Transactional(readOnly = true)
    public QuizSetDetailResponse getSetDetail(Long setId) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        seeder.ensureSeeded();
        QuizSet set = quizSetRepository.findById(setId)
                .filter(item -> item.isBuiltIn() || Objects.equals(item.getOwnerUserId(), viewer.userId()))
                .orElseThrow(() -> new NotFoundException(SET_NOT_FOUND));
        List<QuizQuestionView> questions = quizQuestionRepository.findBySetIdOrderByIdDesc(set.getId())
                .stream()
                .map(QuizQuestionView::from)
                .toList();
        return new QuizSetDetailResponse(QuizSetView.from(set, questions.size()), questions);
    }

    // thêm 1 câu hỏi thủ công vào bộ của phụ huynh (trần 500 câu mỗi bộ)
    @Transactional
    public QuizQuestionView addQuestion(Long setId, QuizQuestionMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());
        if (quizQuestionRepository.countBySetId(set.getId()) >= QuizSet.MAX_QUESTIONS_PER_SET) {
            throw new InvalidRequestException("Mỗi bộ câu hỏi chỉ chứa tối đa 500 câu.");
        }
        QuizOptionKey correct = QuizOptionKey.require(request == null ? null : request.correctOption());
        QuizSubject subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? QuizSubject.require(request.subject())
                : set.getDefaultSubject();
        QuizGrade grade = request != null && request.grade() != null && !request.grade().isBlank()
                ? QuizGrade.require(request.grade())
                : (set.getDefaultGrade() != null ? set.getDefaultGrade() : QuizGrade.LOP_1);
        boolean active = request == null || request.active() == null || request.active();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        QuizQuestion created = new QuizQuestion(
                set.getId(),
                null,
                request == null ? null : request.questionText(),
                request == null ? null : request.optionA(),
                request == null ? null : request.optionB(),
                request == null ? null : request.optionC(),
                request == null ? null : request.optionD(),
                correct,
                request == null ? null : request.explanation(),
                subject,
                grade,
                active,
                now
        );
        return QuizQuestionView.from(quizQuestionRepository.saveAndFlush(created));
    }

    // sửa nội dung hoặc bật tắt 1 câu hỏi trong bộ của phụ huynh
    @Transactional
    public QuizQuestionView updateQuestion(Long setId, Long questionId, QuizQuestionMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());
        QuizQuestion question = quizQuestionRepository.findByIdAndSetId(questionId, set.getId())
                .orElseThrow(() -> new NotFoundException(QUESTION_NOT_FOUND));

        QuizOptionKey correct = request != null && request.correctOption() != null && !request.correctOption().isBlank()
                ? QuizOptionKey.require(request.correctOption())
                : question.getCorrectOption();
        QuizSubject subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? QuizSubject.require(request.subject())
                : question.getSubject();
        QuizGrade grade = request != null && request.grade() != null && !request.grade().isBlank()
                ? QuizGrade.require(request.grade())
                : question.getGrade();

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        question.update(
                request != null && request.questionText() != null ? request.questionText() : question.getQuestionText(),
                request != null && request.optionA() != null ? request.optionA() : question.getOptionA(),
                request != null && request.optionB() != null ? request.optionB() : question.getOptionB(),
                request != null && request.optionC() != null ? request.optionC() : question.getOptionC(),
                request != null && request.optionD() != null ? request.optionD() : question.getOptionD(),
                correct,
                request != null ? request.explanation() : question.getExplanation(),
                subject,
                grade,
                request == null ? null : request.active(),
                now
        );
        return QuizQuestionView.from(quizQuestionRepository.saveAndFlush(question));
    }

    // xóa 1 câu hỏi khỏi bộ của phụ huynh
    @Transactional
    public void deleteQuestion(Long setId, Long questionId, PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());
        QuizQuestion question = quizQuestionRepository.findByIdAndSetId(questionId, set.getId())
                .orElseThrow(() -> new NotFoundException(QUESTION_NOT_FOUND));

        quizAttemptRepository.clearQuestionReference(question.getId());
        quizQuestionRepository.delete(question);
        quizQuestionRepository.flush();
    }

    // kiểm tra CSV trước khi lưu để phụ huynh thấy lỗi từng dòng bằng tiếng Việt (không cần PIN)
    @Transactional(readOnly = true)
    public QuizCsvPreviewResponse previewCsv(Long setId, QuizCsvPreviewRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        QuizSet set = findOwnedSet(setId, viewer.userId());
        long existingCount = quizQuestionRepository.countBySetId(set.getId());
        return csvParser.parseAndValidate(
                request == null ? null : request.csvContent(),
                set.getDefaultSubject(),
                set.getDefaultGrade(),
                existingCount
        ).preview();
    }

    // nhập chỉ các dòng hợp lệ từ CSV vào bộ của phụ huynh trong giới hạn 500 câu mỗi bộ (cần PIN)
    @Transactional
    public QuizCsvImportResponse importCsv(Long setId, QuizCsvImportRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeAndPin(viewer.userId(), request == null ? null : request.pin());
        QuizSet set = requireOwnedSetForUpdate(setId, user.getId());
        long existingCount = quizQuestionRepository.countBySetId(set.getId());

        QuizCsvParser.ParsedCsvResult parsed = csvParser.parseAndValidate(
                request == null ? null : request.csvContent(),
                set.getDefaultSubject(),
                set.getDefaultGrade(),
                existingCount
        );
        if (parsed.preview().headerError() != null) {
            throw new InvalidRequestException(parsed.preview().headerError());
        }
        if (parsed.validRows().isEmpty()) {
            throw new InvalidRequestException("Không có dòng câu hỏi hợp lệ nào để nhập.");
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<QuizQuestion> entities = parsed.validRows().stream()
                .map(row -> new QuizQuestion(
                        set.getId(),
                        null,
                        row.questionText(),
                        row.optionA(),
                        row.optionB(),
                        row.optionC(),
                        row.optionD(),
                        row.correctOption(),
                        row.explanation(),
                        row.subject(),
                        row.grade(),
                        true,
                        now
                ))
                .toList();
        quizQuestionRepository.saveAllAndFlush(entities);

        long totalAfter = quizQuestionRepository.countBySetId(set.getId());
        return new QuizCsvImportResponse(
                entities.size(),
                parsed.preview().invalidCount(),
                totalAfter,
                QuizSetView.from(set, totalAfter)
        );
    }

    // gán bộ câu hỏi ban đầu khi tạo hoặc sửa hồ sơ bé (không truyền thì bật mặc định các bộ có sẵn)
    @Transactional
    public void assignSetsForChild(Long userId, Long profileId, List<Long> requestedSetIds, boolean replaceWhenNull) {
        seeder.ensureSeeded();
        List<Long> resolvedIds;
        if (requestedSetIds == null) {
            if (!replaceWhenNull) {
                return;
            }
            resolvedIds = quizSetRepository.findByBuiltInTrueAndActiveTrueOrderByIdAsc()
                    .stream()
                    .map(QuizSet::getId)
                    .toList();
        } else {
            resolvedIds = validateAssignableSetIds(userId, requestedSetIds);
        }
        childQuizSetRepository.deleteByProfileId(profileId);
        childQuizSetRepository.flush();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<ChildQuizSet> links = resolvedIds.stream()
                .map(setId -> new ChildQuizSet(profileId, setId, now))
                .toList();
        childQuizSetRepository.saveAllAndFlush(links);
    }

    // kiểm tra các mã bộ câu hỏi đều đang bật và thuộc hệ thống hoặc của đúng tài khoản phụ huynh
    public List<Long> validateAssignableSetIds(Long userId, List<Long> rawIds) {
        seeder.ensureSeeded();
        Set<Long> uniqueIds = rawIds == null
                ? Set.of()
                : rawIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            throw new InvalidRequestException("Hãy chọn ít nhất 1 bộ câu hỏi đang bật cho bé.");
        }
        Map<Long, QuizSet> found = quizSetRepository.findByIdIn(uniqueIds).stream()
                .collect(Collectors.toMap(QuizSet::getId, s -> s));
        List<Long> validIds = new ArrayList<>(uniqueIds.size());
        for (Long id : uniqueIds) {
            QuizSet set = found.get(id);
            if (set == null || (!set.isBuiltIn() && !Objects.equals(set.getOwnerUserId(), userId))) {
                throw new NotFoundException(SET_NOT_FOUND);
            }
            if (!set.isActive()) {
                throw new InvalidRequestException("Bộ câu hỏi \"" + set.getName() + "\" đang tạm tắt nên chưa gán được.");
            }
            validIds.add(set.getId());
        }
        return validIds;
    }

    // dọn dữ liệu câu hỏi gắn riêng với 1 hồ sơ bé khi xóa hồ sơ
    @Transactional
    public void deleteForProfile(Long profileId) {
        quizAttemptRepository.deleteByProfileId(profileId);
        childQuizSetRepository.deleteByProfileId(profileId);
    }

    // dọn toàn bộ bộ câu hỏi riêng và lịch sử trả lời khi tài khoản phụ huynh tự xóa (giữ nguyên bộ mặc định)
    @Transactional
    public void deleteForUser(Long userId) {
        quizAttemptRepository.deleteByUserId(userId);
        quizAttemptRepository.clearReferencesByOwnerUserId(userId);
        childQuizSetRepository.deleteByUserId(userId);
        childQuizSetRepository.deleteByOwnerUserId(userId);
        quizQuestionRepository.deleteByOwnerUserId(userId);
        quizSetRepository.deleteByOwnerUserId(userId);
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

    private QuizSet findOwnedSet(Long setId, Long userId) {
        QuizSet set = quizSetRepository.findById(setId)
                .orElseThrow(() -> new NotFoundException(SET_NOT_FOUND));
        if (set.isBuiltIn()) {
            throw new InvalidRequestException(BUILT_IN_READ_ONLY);
        }
        if (!Objects.equals(set.getOwnerUserId(), userId)) {
            throw new NotFoundException(SET_NOT_FOUND);
        }
        return set;
    }

    private QuizSet requireOwnedSetForUpdate(Long setId, Long userId) {
        QuizSet existing = quizSetRepository.findById(setId)
                .orElseThrow(() -> new NotFoundException(SET_NOT_FOUND));
        if (existing.isBuiltIn()) {
            throw new InvalidRequestException(BUILT_IN_READ_ONLY);
        }
        return quizSetRepository.findByIdAndOwnerUserIdForUpdate(setId, userId)
                .orElseThrow(() -> new NotFoundException(SET_NOT_FOUND));
    }

    private Map<Long, Long> countQuestionsBySet(List<QuizSet> sets) {
        if (sets.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = sets.stream().map(QuizSet::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (QuizQuestionRepository.SetQuestionCount row : quizQuestionRepository.countBySetIdIn(ids)) {
            counts.put(row.getSetId(), row.getQuestionCount());
        }
        return counts;
    }
}
