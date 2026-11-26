package com.dippie.dto.movie;

// view thể loại gồm mã, nhãn tiếng Việt với số phim đang hiện
public record MovieGenreView(String id, String label, long count) {
}
