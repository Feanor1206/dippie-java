package com.dippie.dto.vip;

import java.time.LocalDate;
import java.util.List;

// trạng thái phim VIP và các tập VIP đã mở khóa trong ngày hôm nay cho chế độ trẻ em
public record KidVipStatusResponse(
        Long profileId,
        LocalDate day,
        int mathLevel,
        int vipMathLevel,
        List<String> vipSeriesIds,
        List<String> unlockedEpisodeIds
) {

    public static KidVipStatusResponse empty(LocalDate day) {
        return new KidVipStatusResponse(null, day, 0, 0, List.of(), List.of());
    }
}
