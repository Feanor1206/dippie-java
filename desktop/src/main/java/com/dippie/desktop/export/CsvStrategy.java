package com.dippie.desktop.export;

import java.util.List;
import java.util.function.Function;

// strategy cung cấp tiêu đề cột và cách tách 1 dòng dữ liệu thành các ô CSV
public record CsvStrategy<T>(List<String> headers, Function<T, List<?>> cells) {
    public CsvStrategy {
        headers = List.copyOf(headers);
    }
}
