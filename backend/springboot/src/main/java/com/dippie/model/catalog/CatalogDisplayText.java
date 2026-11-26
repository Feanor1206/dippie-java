package com.dippie.model.catalog;

// gom mấy nhãn tiếng Việt cố định để ghép tiêu đề bộ phim với tập phim lên giao diện
public final class CatalogDisplayText {

    public static final String SERIES = "Phim bộ";
    public static final String EPISODE = "Tập phim";

    private CatalogDisplayText() {
    }

    // có số tập thì ghép "Tập N: <tên>", thiếu số tập thì rơi về "Tập phim: <tên>"
    public static String episode(Integer number, String title) {
        return (number == null ? EPISODE : "Tập " + number) + ": " + title;
    }

    public static String series(String title) {
        return SERIES + ": " + title;
    }
}
