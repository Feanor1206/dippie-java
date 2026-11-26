package com.dippie.dto.play;

import java.util.List;

// danh sách tập đang bị khóa chờ ôn tập và các tập bé đã mở khóa
public record RecapStatusView(
        boolean enabled,
        List<RecapGateItemView> pendingGates,
        List<String> unlockedEpisodeIds
) {

    public static RecapStatusView disabled() {
        return new RecapStatusView(false, List.of(), List.of());
    }
}
