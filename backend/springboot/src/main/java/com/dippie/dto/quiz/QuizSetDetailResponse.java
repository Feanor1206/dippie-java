package com.dippie.dto.quiz;

import java.util.List;

// bộ câu hỏi kèm danh sách toàn bộ câu hỏi bên trong
public record QuizSetDetailResponse(QuizSetView set, List<QuizQuestionView> questions) {
}
