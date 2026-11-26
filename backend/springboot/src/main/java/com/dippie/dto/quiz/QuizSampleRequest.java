package com.dippie.dto.quiz;

import java.util.List;

// yêu cầu xem trước 2 đến 3 câu hỏi mẫu theo lớp và các bộ đang chọn
public record QuizSampleRequest(String grade, List<Long> setIds) {
}
