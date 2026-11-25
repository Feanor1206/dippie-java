package com.dippie.desktop.export;

import java.util.List;
import java.util.function.Function;

// Strategy cung cấp tiêu đề cột và cách tách một dòng dữ liệu thành các ô CSV.
public record CsvStrategy<T>(List<String> headers, Function<T, List<?>> cells) {
    public CsvStrategy {
        headers = List.copyOf(headers);
    }
}
