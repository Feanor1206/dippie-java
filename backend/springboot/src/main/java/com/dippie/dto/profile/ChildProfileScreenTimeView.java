package com.dippie.dto.profile;

import com.dippie.model.profile.ChildProfile;
import com.dippie.model.profile.ScreenTimeSummary;

// tóm tắt cài đặt và thời gian dùng hôm nay của 1 hồ sơ bé hiển thị cho phụ huynh
public record ChildProfileScreenTimeView(
        Long profileId,
        String name,
        String avatarKey,
        ScreenTimeStatusView status
) {

    public static ChildProfileScreenTimeView of(ChildProfile profile, ScreenTimeSummary summary) {
        return new ChildProfileScreenTimeView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                ScreenTimeStatusView.from(summary)
        );
    }
}
