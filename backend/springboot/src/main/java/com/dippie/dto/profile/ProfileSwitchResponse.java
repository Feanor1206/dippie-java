package com.dippie.dto.profile;

import com.dippie.model.profile.ChildProfile;
import com.dippie.model.user.User;

// trả về sau khi chuyển sang hồ sơ trẻ em hoặc quay lại hồ sơ chính kèm JWT mới giống lúc đăng nhập
public record ProfileSwitchResponse(
        String token,
        String type,
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        Long activeProfileId,
        ChildProfileView activeProfile
) {

    public static ProfileSwitchResponse of(User user, String token, ChildProfile activeProfile) {
        ChildProfileView profileView = activeProfile == null ? null : ChildProfileView.from(activeProfile);
        return new ProfileSwitchResponse(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                activeProfile == null ? null : activeProfile.getId(),
                profileView
        );
    }
}
