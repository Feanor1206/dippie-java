package com.dippie.admin;

import com.dippie.common.error.InvalidRequestException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    private static final int MAX_PAGE_SIZE = 100;

    AdminPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Tham số phân trang không hợp lệ.");
        }
        return PageRequest.of(page, size);
    }

    static <T> AdminPageResponse<T> of(Page<T> page) {
        return new AdminPageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
