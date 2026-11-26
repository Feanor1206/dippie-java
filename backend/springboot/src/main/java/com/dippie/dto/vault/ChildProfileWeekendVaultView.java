package com.dippie.dto.vault;

import com.dippie.model.profile.ChildProfile;

// tổng quan trạng thái két sắt phim cuối tuần của 1 hồ sơ bé cho màn hình phụ huynh
public record ChildProfileWeekendVaultView(
        Long profileId,
        String name,
        String avatarKey,
        WeekendVaultStatusView status
) {

    public static ChildProfileWeekendVaultView of(ChildProfile profile, WeekendVaultStatusView status) {
        return new ChildProfileWeekendVaultView(profile.getId(), profile.getName(), profile.getAvatarKey(), status);
    }
}
