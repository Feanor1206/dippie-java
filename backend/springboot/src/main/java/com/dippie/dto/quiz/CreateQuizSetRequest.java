package com.dippie.dto.quiz;

// payload tạo mới một bộ câu hỏi riêng của phụ huynh (yêu cầu mã PIN)
public record CreateQuizSetRequest(
        String pin,
        String name,
        String description,
        String defaultSubject,
        String defaultGrade
) {
}
