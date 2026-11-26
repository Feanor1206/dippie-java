package com.dippie.service.profile;

import com.dippie.dto.profile.ChildProfileMutationRequest;
import com.dippie.dto.profile.ChildProfileView;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.profile.ProfileSwitchResponse;
import com.dippie.dto.profile.ProfilesStateResponse;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;
import com.dippie.repository.bounty.BountySubmissionRepository;
import com.dippie.repository.bounty.BountyTaskProfileRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vault.WeekendVaultOverrideRepository;
import com.dippie.repository.vip.ChildVipEpisodeUnlockRepository;
import com.dippie.repository.vip.ChildVipSeriesRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.Viewer;
import com.dippie.service.play.PlaySettingsService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// quản lý CRUD hồ sơ trẻ em (cần PIN) với chuyển qua lại giữa hồ sơ chính và hồ sơ bé bằng JWT mới
@Service
public class ChildProfileService {

    private static final String CHILD_MANAGE_FORBIDDEN = "Hồ sơ trẻ em không thể quản lý hồ sơ.";
    private static final String PARENT_MODE_REQUIRED = "Hãy bật chế độ hồ sơ trẻ em trước.";
    private static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ trẻ em này.";
    private static final String LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository screenTimeLedgerRepository;
    private final BountySubmissionRepository bountySubmissionRepository;
    private final BountyTaskProfileRepository bountyTaskProfileRepository;
    private final WeekendVaultOverrideRepository weekendVaultOverrideRepository;
    private final ChildVipEpisodeUnlockRepository childVipEpisodeUnlockRepository;
    private final ChildVipSeriesRepository childVipSeriesRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final ParentModeService parentModeService;
    private final PlaySettingsService playSettingsService;
    private final JwtService jwtService;
    private final Clock clock;

    public ChildProfileService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository screenTimeLedgerRepository,
            BountySubmissionRepository bountySubmissionRepository,
            BountyTaskProfileRepository bountyTaskProfileRepository,
            WeekendVaultOverrideRepository weekendVaultOverrideRepository,
            ChildVipEpisodeUnlockRepository childVipEpisodeUnlockRepository,
            ChildVipSeriesRepository childVipSeriesRepository,
            WatchProgressRepository watchProgressRepository,
            WatchlistItemRepository watchlistItemRepository,
            FavoriteItemRepository favoriteItemRepository,
            MovieRatingRepository movieRatingRepository,
            ParentModeService parentModeService,
            PlaySettingsService playSettingsService,
            JwtService jwtService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.screenTimeLedgerRepository = screenTimeLedgerRepository;
        this.bountySubmissionRepository = bountySubmissionRepository;
        this.bountyTaskProfileRepository = bountyTaskProfileRepository;
        this.weekendVaultOverrideRepository = weekendVaultOverrideRepository;
        this.childVipEpisodeUnlockRepository = childVipEpisodeUnlockRepository;
        this.childVipSeriesRepository = childVipSeriesRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.movieRatingRepository = movieRatingRepository;
        this.parentModeService = parentModeService;
        this.playSettingsService = playSettingsService;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    // trả trạng thái chế độ phụ huynh, hồ sơ đang mở và danh sách hồ sơ bé (tắt chế độ thì trả rỗng)
    @Transactional(readOnly = true)
    public ProfilesStateResponse getState() {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            return new ProfilesStateResponse(false, null, null, List.of(), ChildProfile.AVATAR_KEYS);
        }
        List<ChildProfileView> profiles = childProfileRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId())
                .stream()
                .map(ChildProfileView::from)
                .toList();
        ChildProfileView activeProfile = viewer.profileId() == null
                ? null
                : profiles.stream()
                        .filter(item -> item.id().equals(viewer.profileId()))
                        .findFirst()
                        .orElse(null);
        return new ProfilesStateResponse(
                true,
                activeProfile == null ? null : activeProfile.id(),
                activeProfile,
                profiles,
                ChildProfile.AVATAR_KEYS
        );
    }

    // khóa dòng user trước khi đếm để 2 request tạo song song cũng không vượt trần 6 hồ sơ
    @Transactional
    public ChildProfileView create(ChildProfileMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        if (childProfileRepository.countByUserId(user.getId()) >= ChildProfile.MAX_PER_ACCOUNT) {
            throw new InvalidRequestException("Mỗi tài khoản chỉ tạo được tối đa 6 hồ sơ trẻ em.");
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        ChildProfile created = new ChildProfile(
                user.getId(),
                request == null ? null : request.name(),
                request == null ? null : request.avatarKey(),
                request == null ? null : request.dailyMinutes(),
                request == null ? null : request.unlimited(),
                now
        );
        return ChildProfileView.from(childProfileRepository.saveAndFlush(created));
    }

    // sửa tên, ảnh đại diện (và tùy chọn hạn mức ngày) của hồ sơ bé, bắt buộc gửi kèm mã PIN phụ huynh
    @Transactional
    public ChildProfileView update(Long profileId, ChildProfileMutationRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        profile.updateDetails(
                request == null ? null : request.name(),
                request == null ? null : request.avatarKey(),
                request == null ? null : request.dailyMinutes(),
                request == null ? null : request.unlimited(),
                now
        );
        return ChildProfileView.from(childProfileRepository.saveAndFlush(profile));
    }

    // xóa hồ sơ trẻ em kèm dọn sạch lịch sử xem, xem sau, yêu thích, đánh giá và các dữ liệu phụ huynh riêng của bé
    @Transactional
    public void delete(Long profileId, PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_MANAGE_FORBIDDEN);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        Long userId = user.getId();
        watchProgressRepository.deleteByUserIdAndProfileScopeId(userId, profile.getId());
        watchlistItemRepository.deleteByUserIdAndProfileScopeId(userId, profile.getId());
        favoriteItemRepository.deleteByUserIdAndProfileScopeId(userId, profile.getId());
        movieRatingRepository.deleteByUserIdAndProfileScopeId(userId, profile.getId());
        bountySubmissionRepository.deleteByProfileId(profile.getId());
        bountyTaskProfileRepository.deleteByProfileId(profile.getId());
        weekendVaultOverrideRepository.deleteByProfileId(profile.getId());
        childVipEpisodeUnlockRepository.deleteByProfileId(profile.getId());
        childVipSeriesRepository.deleteByProfileId(profile.getId());
        screenTimeLedgerRepository.deleteByProfileId(profile.getId());
        playSettingsService.deleteForProfile(profile.getId());
        childProfileRepository.delete(profile);
        childProfileRepository.flush();
    }

    // chuyển sang hồ sơ trẻ em không cần PIN, cấp JWT mới mang claim pid và giữ đúng tokenVersion hiện tại
    @Transactional(readOnly = true)
    public ProfileSwitchResponse switchToChild(Long profileId) {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        ChildProfile profile = childProfileRepository.findByIdAndUserId(profileId, user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        String token = jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion(), profile.getId());
        return ProfileSwitchResponse.of(user, token, profile);
    }

    // thoát hồ sơ trẻ em quay về hồ sơ chính, bắt buộc nhập đúng PIN phụ huynh rồi cấp lại JWT không có pid
    @Transactional(readOnly = true)
    public ProfileSwitchResponse exitChildProfile(PinRequest request) {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(PARENT_MODE_REQUIRED);
        }
        parentModeService.verifyParentPin(user, request == null ? null : request.pin());
        String token = jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
        return ProfileSwitchResponse.of(user, token, null);
    }
}
