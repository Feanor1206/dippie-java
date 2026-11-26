package com.dippie.dto.movie;

import java.math.BigInteger;
import java.util.List;

// một trang kết quả duyệt phim kèm tổng mục, tổng trang với cờ chuyển trang
public record MovieBrowsePage(
        List<MovieView> items,
        BigInteger page,
        int size,
        long totalElements,
        long totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public MovieBrowsePage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    // dùng BigInteger cho page để client gửi số trang siêu to cũng không tràn long lúc tính hasNext
    public static MovieBrowsePage of(List<MovieView> items, BigInteger page, int size, long totalElements) {
        long totalPages = totalElements > 0 ? (totalElements + size - 1) / size : 0;
        boolean hasNext = page.add(BigInteger.ONE).compareTo(BigInteger.valueOf(totalPages)) < 0;
        boolean hasPrevious = page.signum() > 0 && totalPages > 0;
        return new MovieBrowsePage(items, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
}
