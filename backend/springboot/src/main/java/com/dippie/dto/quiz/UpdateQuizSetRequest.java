package com.dippie.dto.quiz;

// payload sửa thông tin hoặc bật tắt bộ câu hỏi của phụ huynh
public record UpdateQuizSetRequest(
        String pin,
        String name,
        String description,
        String defaultSubject,
        String defaultGrade,
        Boolean active
) {
}
