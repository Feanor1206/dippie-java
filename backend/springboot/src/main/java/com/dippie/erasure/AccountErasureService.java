package com.dippie.erasure;

import com.dippie.admin.AuditTrailWriter;
import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.common.error.UnauthorizedException;
import com.dippie.library.FavoriteItemRepository;
import com.dippie.library.MovieRatingRepository;
import com.dippie.library.WatchlistItemRepository;
import com.dippie.passwordreset.PasswordResetTokenRepository;
import com.dippie.progress.WatchProgressRepository;
import com.dippie.report.PlaybackReportRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Xử lý tự xóa tài khoản: xác nhận mật khẩu, dọn sạch dữ liệu cá nhân và chặn xóa quản trị viên hoạt động cuối cùng.
@Service
public class AccountErasureService {

    public static final String ACTION_ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String LAST_ADMIN_MESSAGE = "Hệ thống cần giữ lại ít nhất một quản trị viên đang hoạt động.";
    public static final String WRONG_PASSWORD_MESSAGE = "Mật khẩu hiện tại chưa đúng.";
    public static final String MISSING_PASSWORD_MESSAGE =
            "Hãy nhập mật khẩu hiện tại để xác nhận xóa tài khoản.";

    private static final String TARGET_TYPE_USER = "USER";
    private static final String UNAUTHENTICATED_MESSAGE = "Bạn cần đăng nhập để tiếp tục.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MovieRatingRepository movieRatingRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PlaybackReportRepository playbackReportRepository;
    private final AuditTrailWriter auditTrailWriter;

    public AccountErasureService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MovieRatingRepository movieRatingRepository,
            WatchlistItemRepository watchlistItemRepository,
            FavoriteItemRepository favoriteItemRepository,
            WatchProgressRepository watchProgressRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PlaybackReportRepository playbackReportRepository,
            AuditTrailWriter auditTrailWriter
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.movieRatingRepository = movieRatingRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.playbackReportRepository = playbackReportRepository;
        this.auditTrailWriter = auditTrailWriter;
    }

    // Xóa tài khoản hiện tại trong một transaction, giữ thứ tự khóa khớp với AdminUserService để tránh deadlock.
    @Transactional
    public void deleteCurrentAccount(DeleteAccountRequest request) {
        Viewer viewer = currentViewer.require();
        String password = request == null ? "" : request.rawPassword();
        if (password.isEmpty()) {
            throw new InvalidRequestException(MISSING_PASSWORD_MESSAGE);
        }

        // Việt đừng đổi thứ tự khóa chỗ này: phải khóa danh sách admin trước giống AdminUserService,
        // nếu không thì hai admin vừa sửa quyền vừa tự xóa cùng lúc sẽ deadlock ngay.
        List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
        User user = resolveLockedUser(viewer.userId(), enabledAdmins);

        if (PasswordPolicy.DEFAULT.exceedsMaxBytes(password)
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidRequestException(WRONG_PASSWORD_MESSAGE);
        }

        // Chặn admin hoạt động cuối cùng tự xóa để hệ thống luôn còn ít nhất một người quản trị.
        if (user.isAdmin() && user.isEnabled() && enabledAdmins.size() <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }

        Long userId = user.getId();
        String username = user.getUsername();
        String roleName = user.getRole().name();

        // Xóa dữ liệu cá nhân ở các bảng phụ; riêng báo cáo lỗi phát chỉ gỡ userId để đội vận hành vẫn xem được lỗi phim.
        movieRatingRepository.deleteByUserId(userId);
        watchlistItemRepository.deleteByUserId(userId);
        favoriteItemRepository.deleteByUserId(userId);
        watchProgressRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        playbackReportRepository.clearReporterUser(userId);
        playbackReportRepository.clearResolverUser(userId);

        // Xóa và flush bản ghi user trước khi ghi audit để actorUserId là null, tránh vướng khóa ngoại nhưng vẫn lưu tên cũ.
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
                TARGET_TYPE_USER,
                String.valueOf(userId),
                details
        );
    }

    // Nếu người xóa là admin đang bật thì lấy luôn từ danh sách vừa khóa, khỏi khóa trùng thêm một lượt nữa.
    private User resolveLockedUser(Long userId, List<User> enabledAdmins) {
        return enabledAdmins.stream()
                .filter(candidate -> candidate.getId().equals(userId))
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new UnauthorizedException(UNAUTHENTICATED_MESSAGE)));
    }
}
