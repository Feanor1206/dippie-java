package com.dippie.dto.admin;

import com.dippie.exception.InvalidRequestException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

// Khung phản hồi phân trang dùng chung cho các API quản trị bằng kiểu tổng quát <T>.
public record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    private static final int MAX_PAGE_SIZE = 100;

    public AdminPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    // Chặn size ngoài khoảng 1 đến 100 để tránh truy vấn quá nặng khi client truyền số lớn.
    public static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Số trang hoặc số mục mỗi trang không hợp lệ.");
        }
        return PageRequest.of(page, size);
    }

    // Chuyển kết quả Page của Spring Data sang cấu trúc phân trang của ứng dụng.
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
