package com.dippie.model.quiz;

import com.dippie.exception.InvalidRequestException;

import java.util.Locale;
import java.util.Optional;

// 4 lựa chọn cố định A, B, C, D cho câu hỏi trắc nghiệm
public enum QuizOptionKey {

    A,
    B,
    C,
    D;

    public static QuizOptionKey require(String raw) {
        return parseFlexible(raw)
                .orElseThrow(() -> new InvalidRequestException("Đáp án phải là một trong 4 lựa chọn A, B, C hoặc D."));
    }

    public static Optional<QuizOptionKey> parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "A" -> Optional.of(A);
            case "B" -> Optional.of(B);
            case "C" -> Optional.of(C);
            case "D" -> Optional.of(D);
            default -> Optional.empty();
        };
    }
}
