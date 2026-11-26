package com.dippie.service.bounty;

import com.dippie.dto.bounty.BountyDecisionRequest;
import com.dippie.dto.bounty.BountyDecisionResponse;
import com.dippie.dto.bounty.BountySubmissionView;
import com.dippie.dto.bounty.BountyTaskMutationRequest;
import com.dippie.dto.bounty.BountyTaskView;
import com.dippie.dto.bounty.KidBountyBoardResponse;
import com.dippie.dto.bounty.KidBountyTaskView;
import com.dippie.dto.bounty.ParentBountyBoardResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.profile.ScreenTimeStatusView;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.bounty.BountySubmission;
import com.dippie.model.bounty.BountySubmissionStatus;
import com.dippie.model.bounty.BountyTask;
import com.dippie.model.bounty.BountyTaskProfile;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.model.profile.ScreenTimeSummary;
import com.dippie.model.user.User;
import com.dippie.repository.bounty.BountySubmissionRepository;
import com.dippie.repository.bounty.BountyTaskProfileRepository;
import com.dippie.repository.bounty.BountyTaskRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.profile.ParentModeService;
import com.dippie.service.profile.ScreenTimeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// quản lý bảng nhiệm vụ đời thực: phụ huynh tạo sửa xóa cần PIN, bé nộp nhiệm vụ và ba mẹ duyệt cộng BOUNTY vào sổ cái
@Service
public class BountyBoardService {

    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể chỉnh bảng nhiệm vụ.";
    private static final String CHILD_ONLY_MESSAGE = "Bảng nhiệm vụ đời thực dành cho hồ sơ trẻ em.";
    private static final String TASK_NOT_FOUND = "Không tìm thấy nhiệm vụ này.";
    private static final String SUBMISSION_NOT_FOUND = "Không tìm thấy lượt nộp nhiệm vụ này.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final BountyTaskRepository taskRepository;
    private final BountyTaskProfileRepository taskProfileRepository;
    private final BountySubmissionRepository submissionRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final ParentModeService parentModeService;
    private final ScreenTimeService screenTimeService;
    private final Clock clock;

    public BountyBoardService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            BountyTaskRepository taskRepository,
            BountyTaskProfileRepository taskProfileRepository,
            BountySubmissionRepository submissionRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            ParentModeService parentModeService,
            ScreenTimeService screenTimeService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.taskRepository = taskRepository;
        this.taskProfileRepository = taskProfileRepository;
        this.submissionRepository = submissionRepository;
        this.ledgerRepository = ledgerRepository;
        this.parentModeService = parentModeService;
        this.screenTimeService = screenTimeService;
        this.clock = clock;
    }

    // phụ huynh xem toàn bộ nhiệm vụ đã tạo cùng các lượt nộp gần đây của các bé
    @Transactional(readOnly = true)
    public ParentBountyBoardResponse getParentBoard() {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            return new ParentBountyBoardResponse(List.of(), List.of());
        }
        List<ChildProfile> profiles = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId());
        Map<Long, ChildProfile> profileById = profiles.stream()
                .collect(Collectors.toMap(ChildProfile::getId, Function.identity()));
        List<BountyTask> tasks = taskRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId());
        Map<Long, BountyTask> taskById = tasks.stream()
                .collect(Collectors.toMap(BountyTask::getId, Function.identity()));
        Map<Long, List<Long>> assignedProfilesByTask = loadAssignedProfiles(tasks);

        List<BountyTaskView> taskViews = tasks.stream()
                .map(task -> BountyTaskView.of(task, assignedProfilesByTask.getOrDefault(task.getId(), List.of())))
                .toList();
        List<BountySubmissionView> submissionViews = profiles.isEmpty()
                ? List.of()
                : submissionRepository.findByProfileIdInOrderBySubmittedAtDescIdDesc(profileById.keySet())
                        .stream()
                        .limit(50)
                        .map(sub -> toSubmissionView(sub, taskById.get(sub.getTaskId()), profileById.get(sub.getProfileId())))
                        .toList();
        return new ParentBountyBoardResponse(taskViews, submissionViews);
    }

    // phụ huynh tạo nhiệm vụ mới kèm danh sách hồ sơ bé được nhìn thấy, bắt buộc nhập đúng mã PIN
    @Transactional
    public BountyTaskView createTask(BountyTaskMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeLockedUser(viewer.userId());
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        List<Long> validProfileIds = validateAssignedProfiles(user.getId(), request == null ? null : request.profileIds());

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        BountyTask saved = taskRepository.saveAndFlush(new BountyTask(
                user.getId(),
                request == null ? null : request.title(),
                request == null ? null : request.rewardMinutes(),
                request == null ? null : request.icon(),
                request == null ? null : request.active(),
                request == null ? null : request.repeatableDaily(),
                now
        ));
        saveTaskProfileLinks(saved.getId(), validProfileIds);
        return BountyTaskView.of(saved, validProfileIds);
    }

    // phụ huynh sửa nhiệm vụ hiện có kèm danh sách hồ sơ bé áp dụng
    @Transactional
    public BountyTaskView updateTask(Long taskId, BountyTaskMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeLockedUser(viewer.userId());
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        BountyTask task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new NotFoundException(TASK_NOT_FOUND));
        List<Long> validProfileIds = validateAssignedProfiles(user.getId(), request == null ? null : request.profileIds());

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        task.update(
                request == null ? null : request.title(),
                request == null ? null : request.rewardMinutes(),
                request == null ? null : request.icon(),
                request == null ? null : request.active(),
                request == null ? null : request.repeatableDaily(),
                now
        );
        BountyTask saved = taskRepository.saveAndFlush(task);
        taskProfileRepository.deleteByTaskId(saved.getId());
        taskProfileRepository.flush();
        saveTaskProfileLinks(saved.getId(), validProfileIds);
        return BountyTaskView.of(saved, validProfileIds);
    }

    // phụ huynh xóa hẳn nhiệm vụ và các lượt nộp đi kèm
    @Transactional
    public void deleteTask(Long taskId, PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = requireParentModeLockedUser(viewer.userId());
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        BountyTask task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new NotFoundException(TASK_NOT_FOUND));
        submissionRepository.deleteByTaskId(task.getId());
        taskProfileRepository.deleteByTaskId(task.getId());
        taskRepository.delete(task);
        taskRepository.flush();
    }

    // bé xem bảng nhiệm vụ đang mở của riêng mình cùng các lượt đã nộp để ba mẹ có thể duyệt ngay trên máy
    @Transactional(readOnly = true)
    public KidBountyBoardResponse getKidBoard() {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);

        Set<Long> assignedTaskIds = taskProfileRepository.findByProfileId(profile.getId()).stream()
                .map(BountyTaskProfile::getTaskId)
                .collect(Collectors.toSet());
        List<BountyTask> allAccountTasks = taskRepository.findByUserIdOrderByCreatedAtAscIdAsc(viewer.userId());
        Map<Long, BountyTask> taskById = allAccountTasks.stream()
                .collect(Collectors.toMap(BountyTask::getId, Function.identity()));
        List<BountyTask> visibleTasks = allAccountTasks.stream()
                .filter(task -> task.isActive() && assignedTaskIds.contains(task.getId()))
                .toList();

        List<BountySubmission> profileSubmissions = submissionRepository
                .findByProfileIdOrderBySubmittedAtDescIdDesc(profile.getId());
        Map<Long, Long> pendingIdByTask = new HashMap<>();
        Set<Long> approvedAnyDay = new LinkedHashSet<>();
        Set<Long> approvedToday = new LinkedHashSet<>();
        for (BountySubmission sub : profileSubmissions) {
            if (sub.getStatus() == BountySubmissionStatus.PENDING) {
                pendingIdByTask.putIfAbsent(sub.getTaskId(), sub.getId());
            } else if (sub.getStatus() == BountySubmissionStatus.APPROVED) {
                approvedAnyDay.add(sub.getTaskId());
                if (today.equals(sub.getUsageDay())) {
                    approvedToday.add(sub.getTaskId());
                }
            }
        }

        List<KidBountyTaskView> taskViews = visibleTasks.stream()
                .map(task -> {
                    Long pendingId = pendingIdByTask.get(task.getId());
                    String state;
                    if (pendingId != null) {
                        state = "PENDING";
                    } else if (!task.isRepeatableDaily() && approvedAnyDay.contains(task.getId())) {
                        state = "COMPLETED";
                    } else if (task.isRepeatableDaily() && approvedToday.contains(task.getId())) {
                        state = "COMPLETED_TODAY";
                    } else {
                        state = "AVAILABLE";
                    }
                    return KidBountyTaskView.of(task, state, pendingId);
                })
                .toList();

        List<BountySubmissionView> recentSubmissions = profileSubmissions.stream()
                .filter(sub -> sub.getStatus() == BountySubmissionStatus.PENDING || today.equals(sub.getUsageDay()))
                .limit(20)
                .map(sub -> toSubmissionView(sub, taskById.get(sub.getTaskId()), profile))
                .toList();
        return new KidBountyBoardResponse(profile.getId(), today, taskViews, recentSubmissions);
    }

    // bé bấm Nộp nhiệm vụ -> tạo lượt PENDING, chặn mỗi nhiệm vụ chỉ có 1 lượt PENDING tại 1 thời điểm và kiểm tra lặp ngày/1 lần
    @Transactional
    public BountySubmissionView submitTask(Long taskId) {
        Viewer viewer = currentViewer.require();
        if (!viewer.isChildProfile()) {
            throw new InvalidRequestException(CHILD_ONLY_MESSAGE);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(viewer.profileId(), viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        BountyTask task = taskRepository.findByIdAndUserId(taskId, viewer.userId())
                .orElseThrow(() -> new NotFoundException(TASK_NOT_FOUND));
        if (!taskProfileRepository.existsByTaskIdAndProfileId(task.getId(), profile.getId())) {
            throw new NotFoundException(TASK_NOT_FOUND);
        }
        if (!task.isActive()) {
            throw new InvalidRequestException("Nhiệm vụ này đang tạm tắt.");
        }
        if (submissionRepository.existsByTaskIdAndProfileIdAndStatus(
                task.getId(), profile.getId(), BountySubmissionStatus.PENDING)) {
            throw new ConflictException("Nhiệm vụ này đang chờ ba mẹ duyệt rồi.");
        }
        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        if (!task.isRepeatableDaily()) {
            if (submissionRepository.existsByTaskIdAndProfileIdAndStatus(
                    task.getId(), profile.getId(), BountySubmissionStatus.APPROVED)) {
                throw new ConflictException("Bé đã hoàn thành nhiệm vụ này rồi.");
            }
        } else if (submissionRepository.existsByTaskIdAndProfileIdAndStatusAndUsageDay(
                task.getId(), profile.getId(), BountySubmissionStatus.APPROVED, today)) {
            throw new ConflictException("Hôm nay bé đã nhận thưởng nhiệm vụ này rồi. Mai làm tiếp nhé!");
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        BountySubmission saved = submissionRepository.saveAndFlush(new BountySubmission(
                task.getId(),
                profile.getId(),
                today,
                task.getRewardMinutes(),
                now
        ));
        return toSubmissionView(saved, task, profile);
    }

    // phụ huynh duyệt bằng mã PIN (từ hồ sơ chính hoặc ngay trên máy ở chế độ trẻ em) -> cộng 1 dòng BOUNTY cho hôm nay
    @Transactional
    public BountyDecisionResponse approveSubmission(Long submissionId, BountyDecisionRequest request) {
        return decideSubmission(submissionId, request, true);
    }

    // phụ huynh từ chối lượt nộp bằng mã PIN, không cộng phút để bé có thể làm lại và nộp tiếp
    @Transactional
    public BountyDecisionResponse rejectSubmission(Long submissionId, BountyDecisionRequest request) {
        return decideSubmission(submissionId, request, false);
    }

    private BountyDecisionResponse decideSubmission(Long submissionId, BountyDecisionRequest request, boolean approve) {
        Viewer viewer = currentViewer.require();
        User user = requireParentModeLockedUser(viewer.userId());
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());

        BountySubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException(SUBMISSION_NOT_FOUND));
        if (viewer.isChildProfile() && !viewer.profileId().equals(submission.getProfileId())) {
            throw new NotFoundException(SUBMISSION_NOT_FOUND);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserIdForUpdate(submission.getProfileId(), user.getId())
                .orElseThrow(() -> new NotFoundException(SUBMISSION_NOT_FOUND));
        BountyTask task = taskRepository.findByIdAndUserId(submission.getTaskId(), user.getId())
                .orElseThrow(() -> new NotFoundException(SUBMISSION_NOT_FOUND));
        if (submission.getStatus() != BountySubmissionStatus.PENDING) {
            throw new ConflictException("Lượt nộp nhiệm vụ này đã được xử lý rồi.");
        }

        LocalDate today = ScreenTimeSummary.todayVietnam(clock);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (approve) {
            submission.approve(now);
            submissionRepository.saveAndFlush(submission);
            ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                    profile.getId(),
                    today,
                    ScreenTimeLedgerKind.BOUNTY,
                    submission.getRewardMinutes() * 60,
                    now
            ));
        } else {
            submission.reject(request == null ? null : request.note(), now);
            submissionRepository.saveAndFlush(submission);
        }

        ScreenTimeStatusView status = ScreenTimeStatusView.from(screenTimeService.summarize(profile, today));
        return new BountyDecisionResponse(toSubmissionView(submission, task, profile), status);
    }

    private User requireParentModeLockedUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        return user;
    }

    private List<Long> validateAssignedProfiles(Long userId, List<Long> rawProfileIds) {
        if (rawProfileIds == null || rawProfileIds.isEmpty()) {
            throw new InvalidRequestException("Hãy chọn ít nhất một hồ sơ trẻ em cho nhiệm vụ này.");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : rawProfileIds) {
            if (id == null || id <= 0L) {
                throw new InvalidRequestException("Hồ sơ trẻ em được chọn chưa hợp lệ.");
            }
            uniqueIds.add(id);
        }
        Set<Long> ownedIds = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(ChildProfile::getId)
                .collect(Collectors.toSet());
        for (Long id : uniqueIds) {
            if (!ownedIds.contains(id)) {
                throw new NotFoundException("Không tìm thấy hồ sơ trẻ em này.");
            }
        }
        return List.copyOf(uniqueIds);
    }

    private void saveTaskProfileLinks(Long taskId, List<Long> profileIds) {
        List<BountyTaskProfile> links = profileIds.stream()
                .map(profileId -> new BountyTaskProfile(taskId, profileId))
                .toList();
        taskProfileRepository.saveAllAndFlush(links);
    }

    private Map<Long, List<Long>> loadAssignedProfiles(List<BountyTask> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> taskIds = tasks.stream().map(BountyTask::getId).toList();
        Map<Long, List<Long>> result = new HashMap<>();
        for (BountyTaskProfile link : taskProfileRepository.findByTaskIdIn(taskIds)) {
            result.computeIfAbsent(link.getTaskId(), ignored -> new ArrayList<>()).add(link.getProfileId());
        }
        return result;
    }

    private static BountySubmissionView toSubmissionView(
            BountySubmission submission,
            BountyTask task,
            ChildProfile profile
    ) {
        return BountySubmissionView.of(
                submission,
                task == null ? "" : task.getTitle(),
                task == null ? null : task.getIcon(),
                profile == null ? "" : profile.getName(),
                profile == null ? "bear" : profile.getAvatarKey()
        );
    }
}
