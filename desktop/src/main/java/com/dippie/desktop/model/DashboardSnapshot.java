package com.dippie.desktop.model;

import java.util.List;
import java.util.Map;

// trọn gói dữ liệu cho 1 lần tải tab Tổng quan để cập nhật 5 thẻ, 2 bảng với biểu đồ cùng lúc
// kéo từ DashboardService ra đây để controller với view trao đổi qua model thôi nha Việt
public record DashboardSnapshot(DashboardCounts counts, List<TopRated> topRated, List<MostWatched> mostWatched,
                                Map<Integer, Long> ratingCounts) {
}
