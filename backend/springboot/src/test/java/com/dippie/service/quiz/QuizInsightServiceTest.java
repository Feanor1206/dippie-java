package com.dippie.service.quiz;

import com.dippie.dto.quiz.GradeSuggestionView;
import com.dippie.model.quiz.QuizGrade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// test đúng ngưỡng gợi ý nâng lớp (> 90% qua ít nhất 20 câu) và hạ lớp (< 50% qua ít nhất 10 câu)
class QuizInsightServiceTest {

    @Test
    void suggestsMovingUpOnlyWhenStrictlyAboveNinetyPercentOverAtLeastTwentyQuestions() {
        // dưới 20 câu -> chưa đủ dữ liệu, chưa gợi ý
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_2, 19, 19)).isNull();

        // đúng đúng 90% (18/20) -> chưa lớn hơn 90%, chưa gợi ý
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_2, 20, 18)).isNull();

        // 19/20 = 95% > 90% -> gợi ý lên lớp 3 bằng lời tiếng Việt dễ hiểu
        GradeSuggestionView up = QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_2, 20, 19);
        assertThat(up).isNotNull();
        assertThat(up.direction()).isEqualTo("UP");
        assertThat(up.currentGrade()).isEqualTo("LOP_2");
        assertThat(up.suggestedGrade()).isEqualTo("LOP_3");
        assertThat(up.accuracyPercent()).isEqualTo(95);
        assertThat(up.message()).contains("Lớp 3");

        // đang ở lớp cao nhất (Lớp 5) -> không gợi ý lên nữa
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_5, 25, 25)).isNull();
    }

    @Test
    void suggestsMovingDownOnlyWhenStrictlyBelowFiftyPercentOverAtLeastTenQuestions() {
        // dưới 10 câu -> chưa gợi ý
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_3, 9, 0)).isNull();

        // đúng đúng 50% (5/10) -> chưa nhỏ hơn 50%, chưa gợi ý
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_3, 10, 5)).isNull();

        // 4/10 = 40% < 50% -> gợi ý hạ xuống lớp 2
        GradeSuggestionView down = QuizInsightService.evaluateGradeSuggestion(QuizGrade.LOP_3, 10, 4);
        assertThat(down).isNotNull();
        assertThat(down.direction()).isEqualTo("DOWN");
        assertThat(down.currentGrade()).isEqualTo("LOP_3");
        assertThat(down.suggestedGrade()).isEqualTo("LOP_2");
        assertThat(down.accuracyPercent()).isEqualTo(40);
        assertThat(down.message()).contains("Lớp 2");

        // đang ở Mẫu giáo -> không gợi ý hạ nữa
        assertThat(QuizInsightService.evaluateGradeSuggestion(QuizGrade.MAU_GIAO, 15, 2)).isNull();
    }
}
