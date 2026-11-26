package com.dippie.service.catalog;

import java.util.List;

// thêm mấy tên gọi tắt hay cách gõ quen mà người xem hay đánh khi tìm phim bộ
public final class SeriesSearchAliases {

    private SeriesSearchAliases() {
    }

    // trả danh sách biệt danh tìm kiếm theo seriesId, chưa khai báo thì trả list rỗng
    public static List<String> forSeries(String seriesId) {
        return switch (seriesId) {
            case "ser_peppa_pig" -> List.of("Peppa");
            case "ser_jadoo" -> List.of("Jadoo");
            case "ser_trang_quynh" -> List.of("Trạng Quỳnh");
            case "ser_ori" -> List.of("Ori");
            case "ser_doraemon" -> List.of("Doremon");
            default -> List.of();
        };
    }
}
