package com.dippie.dto.quiz;

// payload xác nhận đổi lớp học cho bé bằng mã PIN phụ huynh từ gợi ý báo cáo tuần
public record ConfirmGradeChangeRequest(String pin, String grade) {
}
