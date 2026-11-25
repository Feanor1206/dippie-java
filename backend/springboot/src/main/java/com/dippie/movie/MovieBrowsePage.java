package com.dippie.movie;

import java.math.BigInteger;
import java.util.List;

public record MovieBrowsePage(
        List<MovieView> items,
        BigInteger page,
        int size,
        long totalElements,
        long totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static MovieBrowsePage of(List<MovieView> items, BigInteger page, int size, long totalElements) {
        long totalPages = totalElements > 0 ? (totalElements + size - 1) / size : 0;
        boolean hasNext = page.add(BigInteger.ONE).compareTo(BigInteger.valueOf(totalPages)) < 0;
        boolean hasPrevious = page.signum() > 0 && totalPages > 0;
        return new MovieBrowsePage(items, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
}
