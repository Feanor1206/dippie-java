package com.dippie.dto.profile;

import java.util.List;

// gom trạng thái chế độ phụ huynh, hồ sơ đang chọn và danh sách hồ sơ bé của tài khoản
public record ProfilesStateResponse(
        boolean parentModeEnabled,
        Long activeProfileId,
        ChildProfileView activeProfile,
        List<ChildProfileView> profiles,
        List<String> availableAvatars
) {
}
