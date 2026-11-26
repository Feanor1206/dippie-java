package com.dippie.controller.profile;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.ChildProfileMutationRequest;
import com.dippie.dto.profile.ChildProfileScreenTimeView;
import com.dippie.dto.profile.ChildProfileView;
import com.dippie.dto.profile.ParentTimeGrantRequest;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.profile.ProfileSwitchResponse;
import com.dippie.dto.profile.ProfilesStateResponse;
import com.dippie.dto.profile.UpdateScreenTimeSettingsRequest;
import com.dippie.service.profile.ChildProfileService;
import com.dippie.service.profile.ScreenTimeService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/profiles cho đọc danh sách hồ sơ bé, thêm sửa xóa cần PIN và chuyển hồ sơ nhận JWT mới
@RestController
@RequestMapping("/api/users/profiles")
public class ChildProfileController {

    private final ChildProfileService childProfileService;
    private final ScreenTimeService screenTimeService;

    public ChildProfileController(ChildProfileService childProfileService, ScreenTimeService screenTimeService) {
        this.childProfileService = childProfileService;
        this.screenTimeService = screenTimeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfilesStateResponse>> list() {
        return ResponseEntity.ok(ApiResponse.ok(childProfileService.getState()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChildProfileView>> create(
            @RequestBody(required = false) ChildProfileMutationRequest request
    ) {
        ChildProfileView created = childProfileService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã tạo hồ sơ trẻ em."));
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<ApiResponse<ChildProfileView>> update(
            @PathVariable Long profileId,
            @RequestBody(required = false) ChildProfileMutationRequest request
    ) {
        ChildProfileView updated = childProfileService.update(profileId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật hồ sơ trẻ em."));
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long profileId,
            @RequestBody(required = false) PinRequest request
    ) {
        childProfileService.delete(profileId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{profileId}/switch")
    public ResponseEntity<ApiResponse<ProfileSwitchResponse>> switchToChild(@PathVariable Long profileId) {
        return ResponseEntity.ok(ApiResponse.ok(
                childProfileService.switchToChild(profileId),
                "Đã chuyển sang hồ sơ trẻ em."
        ));
    }

    @PostMapping("/exit")
    public ResponseEntity<ApiResponse<ProfileSwitchResponse>> exitChildProfile(
            @RequestBody(required = false) PinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                childProfileService.exitChildProfile(request),
                "Đã quay lại tài khoản chính."
        ));
    }

    @GetMapping("/screen-time")
    public ResponseEntity<ApiResponse<List<ChildProfileScreenTimeView>>> listScreenTime() {
        return ResponseEntity.ok(ApiResponse.ok(screenTimeService.listParentOverview()));
    }

    @PutMapping("/{profileId}/screen-time")
    public ResponseEntity<ApiResponse<ChildProfileScreenTimeView>> updateScreenTimeSettings(
            @PathVariable Long profileId,
            @RequestBody(required = false) UpdateScreenTimeSettingsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                screenTimeService.updateSettings(profileId, request),
                "Đã lưu cài đặt thời gian xem."
        ));
    }

    @PostMapping("/{profileId}/screen-time/grant")
    public ResponseEntity<ApiResponse<ChildProfileScreenTimeView>> grantExtraMinutes(
            @PathVariable Long profileId,
            @RequestBody(required = false) ParentTimeGrantRequest request
    ) {
        ChildProfileScreenTimeView updated = screenTimeService.grantExtraMinutes(profileId, request);
        int granted = request == null || request.minutes() == null ? 0 : request.minutes();
        return ResponseEntity.ok(ApiResponse.ok(
                updated,
                "Đã cộng thêm " + granted + " phút xem phim cho bé."
        ));
    }
}
