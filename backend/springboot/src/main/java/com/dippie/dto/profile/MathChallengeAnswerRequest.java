package com.dippie.dto.profile;

// payload bé nộp đáp án kèm token câu hỏi đã nhận từ server
public record MathChallengeAnswerRequest(String token, Integer answer) {
}
