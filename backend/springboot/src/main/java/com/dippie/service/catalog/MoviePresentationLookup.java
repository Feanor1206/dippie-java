package com.dippie.service.catalog;

import java.util.Map;

// cổng Adapter (vai Target) tra rating với ảnh trình bày từ bảng movies cũ để tầng catalog khỏi import gói movie
public interface MoviePresentationLookup {

    // trả map điểm, poster với backdrop theo mã của mấy tập đã ready
    Map<String, Presentation> findForReadyEpisodes();

    // bộ ba rating, poster với backdrop đọc từ bảng Movie
    record Presentation(double rating, String poster, String backdrop) {
    }
}
