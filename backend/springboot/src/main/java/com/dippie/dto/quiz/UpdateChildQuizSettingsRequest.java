package com.dippie.dto.quiz;

import java.util.List;

// payload phụ huynh chọn lớp và các bộ câu hỏi đang bật cho 1 hồ sơ bé
public record UpdateChildQuizSettingsRequest(String pin, String grade, List<Long> activeSetIds) {
}
