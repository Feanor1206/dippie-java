package com.dippie.service.catalog;

import java.util.Map;

// Cổng Adapter (vai trò Target) tra cứu điểm đánh giá và ảnh trình bày từ bảng movies kế thừa mà tầng catalog không cần import gói movie.
public interface MoviePresentationLookup {

    // Trả map điểm, poster và backdrop theo mã của các tập đã ở trạng thái ready.
    Map<String, Presentation> findForReadyEpisodes();

    // Bộ ba thông tin trình bày (điểm đánh giá, poster, backdrop) đọc từ bảng Movie.
    record Presentation(double rating, String poster, String backdrop) {
    }
}
