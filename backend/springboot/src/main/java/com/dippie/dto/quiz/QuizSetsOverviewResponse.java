package com.dippie.dto.quiz;

import java.util.List;

// tổng quan ngân hàng câu hỏi của tài khoản phụ huynh kèm các giới hạn và danh mục lớp/môn
public record QuizSetsOverviewResponse(
        List<QuizSetView> sets,
        int ownSetCount,
        int maxSetsPerAccount,
        int maxQuestionsPerSet,
        int maxCsvBytes,
        List<QuizGradeMeta> grades,
        List<QuizSubjectMeta> subjects
) {
}
