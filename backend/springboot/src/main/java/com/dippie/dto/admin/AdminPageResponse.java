package com.dippie.dto.admin;

import com.dippie.exception.InvalidRequestException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

// khung phân trang dùng chung kiểu generics <T> cho mấy API admin
public record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public AdminPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    // chặn size ngoài 1 đến 100 kẻo client gửi số to làm DB gồng
    public static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Số trang hoặc số mục mỗi trang không hợp lệ.");
        }
        return PageRequest.of(page, size);
    }

    // đổi Page của Spring Data sang format phân trang của app
    public static <T> AdminPageResponse<T> of(Page<T> page) {
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
