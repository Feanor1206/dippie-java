package com.dippie.desktop.model;

import java.util.List;
import java.util.Map;

// Toàn bộ dữ liệu của một lần tải tab Tổng quan để cập nhật 5 thẻ, hai bảng và biểu đồ cùng lúc.
// Mình kéo từ DashboardService ra đây để controller với view trao đổi qua model thôi nha Việt.
public record DashboardSnapshot(DashboardCounts counts, List<TopRated> topRated, List<MostWatched> mostWatched,
                                Map<Integer, Long> ratingCounts) {
}
