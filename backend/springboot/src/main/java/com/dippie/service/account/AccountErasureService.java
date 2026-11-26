package com.dippie.service.account;

import com.dippie.dto.account.DeleteAccountRequest;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.account.PasswordResetTokenRepository;
import com.dippie.repository.bounty.BountySubmissionRepository;
import com.dippie.repository.bounty.BountyTaskProfileRepository;
import com.dippie.repository.bounty.BountyTaskRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.report.PlaybackReportRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vault.WeekendVaultOverrideRepository;
import com.dippie.repository.vip.ChildVipEpisodeUnlockRepository;
import com.dippie.repository.vip.ChildVipSeriesRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Viewer;
import com.dippie.service.admin.AuditTrailWriter;
import com.dippie.service.play.PlaySettingsService;
import com.dippie.service.quiz.QuizBankService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// luồng tự xóa tài khoản, check mật khẩu, dọn dữ liệu cá nhân và chặn xóa admin hoạt động cuối cùng
@Service
public class AccountErasureService {

    public static final String ACTION_ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String LAST_ADMIN_MESSAGE = "Hệ thống cần giữ lại ít nhất một quản trị viên đang hoạt động.";
    public static final String WRONG_PASSWORD_MESSAGE = "Mật khẩu hiện tại chưa đúng.";
    public static final String MISSING_PASSWORD_MESSAGE =
            "Hãy nhập mật khẩu hiện tại để xác nhận xóa tài khoản.";


    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MovieRatingRepository movieRatingRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository screenTimeLedgerRepository;
    private final BountySubmissionRepository bountySubmissionRepository;
    private final BountyTaskProfileRepository bountyTaskProfileRepository;
    private final BountyTaskRepository bountyTaskRepository;
    private final WeekendVaultOverrideRepository weekendVaultOverrideRepository;
    private final ChildVipEpisodeUnlockRepository childVipEpisodeUnlockRepository;
    private final ChildVipSeriesRepository childVipSeriesRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PlaybackReportRepository playbackReportRepository;
    private final PlaySettingsService playSettingsService;
    private final QuizBankService quizBankService;
    private final AuditTrailWriter auditTrailWriter;

    public AccountErasureService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MovieRatingRepository movieRatingRepository,
            WatchlistItemRepository watchlistItemRepository,
            FavoriteItemRepository favoriteItemRepository,
            WatchProgressRepository watchProgressRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository screenTimeLedgerRepository,
            BountySubmissionRepository bountySubmissionRepository,
            BountyTaskProfileRepository bountyTaskProfileRepository,
            BountyTaskRepository bountyTaskRepository,
            WeekendVaultOverrideRepository weekendVaultOverrideRepository,
            ChildVipEpisodeUnlockRepository childVipEpisodeUnlockRepository,
            ChildVipSeriesRepository childVipSeriesRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PlaybackReportRepository playbackReportRepository,
            PlaySettingsService playSettingsService,
            QuizBankService quizBankService,
            AuditTrailWriter auditTrailWriter
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.movieRatingRepository = movieRatingRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.childProfileRepository = childProfileRepository;
        this.screenTimeLedgerRepository = screenTimeLedgerRepository;
        this.bountySubmissionRepository = bountySubmissionRepository;
        this.bountyTaskProfileRepository = bountyTaskProfileRepository;
        this.bountyTaskRepository = bountyTaskRepository;
        this.weekendVaultOverrideRepository = weekendVaultOverrideRepository;
        this.childVipEpisodeUnlockRepository = childVipEpisodeUnlockRepository;
        this.childVipSeriesRepository = childVipSeriesRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.playbackReportRepository = playbackReportRepository;
        this.playSettingsService = playSettingsService;
        this.quizBankService = quizBankService;
        this.auditTrailWriter = auditTrailWriter;
    }

    // xóa tài khoản trong 1 transaction, chặn hồ sơ trẻ em và giữ thứ tự khóa khớp AdminUserService kẻo deadlock
    @Transactional
    public void deleteCurrentAccount(DeleteAccountRequest request) {
        Viewer viewer = currentViewer.requireMainProfile("Hồ sơ trẻ em không thể xóa tài khoản.");
        String password = request == null ? "" : request.rawPassword();
        if (password.isEmpty()) {
            throw new InvalidRequestException(MISSING_PASSWORD_MESSAGE);
        }

        // Việt đừng đổi thứ tự khóa chỗ này, phải khóa danh sách admin trước giống AdminUserService
        // không thì 2 admin vừa sửa quyền vừa tự xóa cùng lúc là deadlock ngay
        List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
        User user = resolveLockedUser(viewer.userId(), enabledAdmins);

        if (PasswordPolicy.DEFAULT.exceedsMaxBytes(password)
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidRequestException(WRONG_PASSWORD_MESSAGE);
        }

        // chặn admin đang bật cuối cùng tự xóa kẻo hệ thống mất sạch người quản trị
        if (user.isAdmin() && user.isEnabled() && enabledAdmins.size() <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }

        Long userId = user.getId();
        String username = user.getUsername();
        String roleName = user.getRole().name();

        // dọn dữ liệu cá nhân ở mấy bảng phụ trước rồi xóa hết hồ sơ trẻ em của tài khoản
        movieRatingRepository.deleteByUserId(userId);
        watchlistItemRepository.deleteByUserId(userId);
        favoriteItemRepository.deleteByUserId(userId);
        watchProgressRepository.deleteByUserId(userId);
        bountySubmissionRepository.deleteByUserId(userId);
        bountyTaskProfileRepository.deleteByUserId(userId);
        bountyTaskRepository.deleteByUserId(userId);
        weekendVaultOverrideRepository.deleteByUserId(userId);
        childVipEpisodeUnlockRepository.deleteByUserId(userId);
        childVipSeriesRepository.deleteByUserId(userId);
        screenTimeLedgerRepository.deleteByUserId(userId);
        playSettingsService.deleteForUser(userId);
        quizBankService.deleteForUser(userId);
        childProfileRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        playbackReportRepository.clearReporterUser(userId);
        playbackReportRepository.clearResolverUser(userId);

        // xóa rồi flush user trước khi ghi audit để actorUserId là null, khỏi vướng khóa ngoại mà vẫn giữ tên cũ
        userRepository.delete(user);
        userRepository.flush();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", username);
        details.put("role", roleName);
        details.put("selfInitiated", true);
        auditTrailWriter.recordForActor(
                null,
                username,
                ACTION_ACCOUNT_DELETED,
                "USER",
                String.valueOf(userId),
                details
        );
    }

    // đang là admin bật sẵn thì lấy luôn từ list vừa khóa, khỏi khóa trùng thêm lượt nữa
    private User resolveLockedUser(Long userId, List<User> enabledAdmins) {
        return enabledAdmins.stream()
                .filter(candidate -> candidate.getId().equals(userId))
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục.")));
    }
}
