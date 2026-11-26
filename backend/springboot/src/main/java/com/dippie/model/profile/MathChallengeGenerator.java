package com.dippie.model.profile;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

// sinh phép tính ngẫu nhiên theo 4 mức toán của bé, đáp án luôn là số nguyên không âm
public final class MathChallengeGenerator {

    private final RandomGenerator random;

    public MathChallengeGenerator() {
        this(new SecureRandom());
    }

    public MathChallengeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public MathQuestion generate(int rawLevel) {
        int level = ChildProfile.validateMathLevel(rawLevel);
        return switch (level) {
            case 1 -> generateAddOrSubtract(10, 1, 2, level);
            case 2 -> generateAddOrSubtract(20, 2, 5, level);
            case 3 -> generateAddOrSubtract(100, 10, 15, level);
            case 4 -> generateMultiplicationOrDivision(level);
            default -> throw new IllegalStateException("Mức toán không hợp lệ: " + level);
        };
    }

    private MathQuestion generateAddOrSubtract(int maxTotal, int minAddend, int minMinuend, int level) {
        if (random.nextBoolean()) {
            int first = random.nextInt(minAddend, maxTotal - minAddend + 1);
            int second = random.nextInt(1, maxTotal - first + 1);
            return new MathQuestion(first + " + " + second + " = ?", first + second, level);
        }
        int first = random.nextInt(minMinuend, maxTotal + 1);
        int second = random.nextInt(1, first);
        return new MathQuestion(first + " - " + second + " = ?", first - second, level);
    }

    private MathQuestion generateMultiplicationOrDivision(int level) {
        int a = random.nextInt(2, 10);
        int b = random.nextInt(2, 10);
        if (random.nextBoolean()) {
            return new MathQuestion(a + " × " + b + " = ?", a * b, level);
        }
        return new MathQuestion((a * b) + " ÷ " + a + " = ?", b, level);
    }

    // cặp đề toán hiển thị cho bé kèm đáp án chuẩn để mã hóa trong token phía server
    public record MathQuestion(String question, int answer, int level) {
    }
}
