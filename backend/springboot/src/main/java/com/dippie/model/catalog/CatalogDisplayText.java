package com.dippie.model.catalog;

// Gom mấy nhãn tiếng Việt cố định dùng khi ghép tiêu đề phim bộ và tập phim để hiển thị ra giao diện.
public final class CatalogDisplayText {

    public static final String SERIES = "Phim bộ";
    public static final String EPISODE = "Tập phim";

    private CatalogDisplayText() {
    }

    // Có số tập thì ghép dạng "Tập N: ...", thiếu số tập thì rơi về tiền tố "Tập phim: ...".
    public static String episode(Integer number, String title) {
        return (number == null ? EPISODE : "Tập " + number) + ": " + title;
    }

    public static String series(String title) {
        return SERIES + ": " + title;
    }
}
