package com.dippie.dto.movie;

// DTO trả mã thể loại, nhãn tiếng Việt và số phim đang hiển thị của từng thể loại.
public record MovieGenreView(String id, String label, long count) {
}
