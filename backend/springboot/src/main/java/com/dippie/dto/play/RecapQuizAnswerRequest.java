package com.dippie.dto.play;

import java.util.List;

// câu trả lời ôn tập của bé: gửi orderedIds nếu xếp thứ tự cảnh, hoặc choiceId nếu chọn trắc nghiệm
public record RecapQuizAnswerRequest(String token, List<String> orderedIds, String choiceId) {
}
