package com.dippie.model.quiz;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

// sinh câu hỏi toán trắc nghiệm 4 đáp án theo từng lớp với đáp án nhiễu gần đúng và không ra số âm
public final class MathQuestionGenerator {

    private final RandomGenerator random;

    public MathQuestionGenerator() {
        this(new SecureRandom());
    }

    public MathQuestionGenerator(RandomGenerator random) {
        this.random = random;
    }

    // thử tối đa 24 lần để né các câu vừa hỏi gần đây của bé
    public GeneratedMathQuestion generateAvoiding(QuizGrade grade, Set<String> avoidKeys) {
        QuizGrade safeGrade = grade == null ? QuizGrade.LOP_1 : grade;
        GeneratedMathQuestion last = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            last = generate(safeGrade);
            if (avoidKeys == null || !avoidKeys.contains(last.questionKey())) {
                return last;
            }
        }
        return last;
    }

    public GeneratedMathQuestion generate(QuizGrade grade) {
        QuizGrade safeGrade = grade == null ? QuizGrade.LOP_1 : grade;
        return switch (safeGrade) {
            case MAU_GIAO -> generateAddOrSubtract(safeGrade, 10, 1, 2, false);
            case LOP_1 -> generateAddOrSubtract(safeGrade, 20, 2, 5, false);
            case LOP_2 -> generateGrade2(safeGrade);
            case LOP_3 -> generateTables2To9(safeGrade);
            case LOP_4 -> generateGrade4(safeGrade);
            case LOP_5 -> generateGrade5(safeGrade);
        };
    }

    private GeneratedMathQuestion generateAddOrSubtract(
            QuizGrade grade,
            int maxTotal,
            int minAddend,
            int minMinuend,
            boolean includeTensDistractors
    ) {
        boolean addition = random.nextBoolean();
        if (addition) {
            int first = random.nextInt(minAddend, maxTotal - minAddend + 1);
            int second = random.nextInt(1, maxTotal - first + 1);
            int answer = first + second;
            String expr = first + " + " + second;
            List<Integer> preferred = new ArrayList<>();
            preferred.add(answer + 1);
            preferred.add(answer - 1);
            preferred.add(answer + 2);
            preferred.add(answer - 2);
            if (includeTensDistractors) {
                preferred.add(answer + 10);
                preferred.add(answer - 10);
                preferred.add(answer + 9);
            }
            if (first > second) {
                preferred.add(first - second);
            }
            return buildQuestion(
                    grade,
                    expr,
                    expr + " = ?",
                    answer,
                    expr + " = " + answer,
                    preferred,
                    0,
                    maxTotal + (includeTensDistractors ? 15 : 4)
            );
        }
        int first = random.nextInt(minMinuend, maxTotal + 1);
        int second = random.nextInt(1, first);
        int answer = first - second;
        String expr = first + " - " + second;
        List<Integer> preferred = new ArrayList<>();
        preferred.add(answer + 1);
        preferred.add(answer - 1);
        preferred.add(answer + 2);
        preferred.add(answer - 2);
        if (includeTensDistractors) {
            preferred.add(answer + 10);
            preferred.add(answer - 10);
        }
        preferred.add(first + second);
        return buildQuestion(
                grade,
                expr,
                expr + " = ?",
                answer,
                expr + " = " + answer,
                preferred,
                0,
                maxTotal + (includeTensDistractors ? 15 : 4)
        );
    }

    private GeneratedMathQuestion generateGrade2(QuizGrade grade) {
        if (random.nextInt(4) == 0) {
            int a = random.nextInt(2, 6);
            int b = random.nextInt(2, 10);
            int answer = a * b;
            String expr = a + " × " + b;
            List<Integer> preferred = List.of(
                    (a + 1) * b,
                    Math.max(1, (a - 1) * b),
                    a * (b + 1),
                    a + b,
                    answer + 2,
                    answer - 2
            );
            return buildQuestion(grade, expr, expr + " = ?", answer, expr + " = " + answer, preferred, 1, 60);
        }
        return generateAddOrSubtract(grade, 100, 10, 18, true);
    }

    // lớp 3 dùng đúng bảng nhân chia từ 2 đến 9
    private GeneratedMathQuestion generateTables2To9(QuizGrade grade) {
        int a = random.nextInt(2, 10);
        int b = random.nextInt(2, 10);
        if (random.nextBoolean()) {
            int answer = a * b;
            String expr = a + " × " + b;
            List<Integer> preferred = List.of(
                    (a + 1) * b,
                    (a - 1) * b,
                    a * (b + 1),
                    a * (b - 1),
                    a + b,
                    answer + 2,
                    answer - 2
            );
            return buildQuestion(grade, expr, expr + " = ?", answer, expr + " = " + answer, preferred, 1, 90);
        }
        int dividend = a * b;
        int answer = b;
        String expr = dividend + " ÷ " + a;
        List<Integer> preferred = List.of(
                answer + 1,
                answer - 1,
                answer + 2,
                answer - 2,
                a,
                answer + 3
        );
        String explanation = dividend + " ÷ " + a + " = " + answer + " vì " + a + " × " + answer + " = " + dividend;
        return buildQuestion(grade, expr, expr + " = ?", answer, explanation, preferred, 1, 15);
    }

    private GeneratedMathQuestion generateGrade4(QuizGrade grade) {
        int pick = random.nextInt(3);
        if (pick == 0) {
            int a = random.nextInt(12, 49);
            int b = random.nextInt(2, 10);
            int answer = a * b;
            String expr = a + " × " + b;
            List<Integer> preferred = List.of(
                    answer + b,
                    answer - b,
                    answer + 10,
                    answer - 10,
                    answer + 2,
                    answer - 2
            );
            return buildQuestion(grade, expr, expr + " = ?", answer, expr + " = " + answer, preferred, 1, 500);
        }
        if (pick == 1) {
            int divisor = random.nextInt(3, 10);
            int quotient = random.nextInt(11, 31);
            int dividend = divisor * quotient;
            String expr = dividend + " ÷ " + divisor;
            List<Integer> preferred = List.of(
                    quotient + 1,
                    quotient - 1,
                    quotient + 2,
                    quotient - 2,
                    quotient + 10,
                    quotient - 10
            );
            String explanation = dividend + " ÷ " + divisor + " = " + quotient + " vì " + divisor + " × " + quotient + " = " + dividend;
            return buildQuestion(grade, expr, expr + " = ?", quotient, explanation, preferred, 1, 60);
        }
        return generateAddOrSubtract(grade, 1000, 120, 250, true);
    }

    private GeneratedMathQuestion generateGrade5(QuizGrade grade) {
        int pick = random.nextInt(3);
        if (pick == 0) {
            int a = random.nextInt(4, 15);
            int b = random.nextInt(3, 10);
            int c = random.nextInt(5, 25);
            int answer = a * b + c;
            String expr = a + " × " + b + " + " + c;
            List<Integer> preferred = List.of(
                    a * (b + c),
                    answer + 10,
                    answer - 10,
                    answer + b,
                    answer - b,
                    answer + 2
            );
            String explanation = "Tính nhân trước: " + a + " × " + b + " = " + (a * b) + ", rồi cộng " + c + " được " + answer;
            return buildQuestion(grade, expr, expr + " = ?", answer, explanation, preferred, 1, 400);
        }
        if (pick == 1) {
            int[] percents = {10, 20, 25, 50};
            int[] bases = {40, 60, 80, 100, 120, 160, 200};
            int pct = percents[random.nextInt(percents.length)];
            int base = bases[random.nextInt(bases.length)];
            int answer = (base * pct) / 100;
            String key = pct + "%_of_" + base;
            String question = pct + "% của " + base + " là bao nhiêu?";
            List<Integer> preferred = List.of(
                    answer + 5,
                    Math.max(1, answer - 5),
                    answer * 2,
                    Math.max(1, answer / 2),
                    answer + 10,
                    Math.max(1, answer - 10)
            );
            String explanation = pct + "% của " + base + " = " + base + " × " + pct + " ÷ 100 = " + answer;
            return buildQuestion(grade, key, question, answer, explanation, preferred, 1, base);
        }
        int a = random.nextInt(12, 26);
        int b = random.nextInt(11, 19);
        int answer = a * b;
        String expr = a + " × " + b;
        List<Integer> preferred = List.of(
                answer + 10,
                answer - 10,
                answer + a,
                answer - a,
                answer + b,
                answer - b
        );
        return buildQuestion(grade, expr, expr + " = ?", answer, expr + " = " + answer, preferred, 1, 600);
    }

    private GeneratedMathQuestion buildQuestion(
            QuizGrade grade,
            String keySuffix,
            String questionText,
            int answer,
            String explanation,
            List<Integer> preferredDistractors,
            int minValue,
            int maxValue
    ) {
        LinkedHashSet<Integer> wrongSet = new LinkedHashSet<>();
        List<Integer> candidates = new ArrayList<>(preferredDistractors);
        Collections.shuffle(candidates, toUtilRandom());
        for (int candidate : candidates) {
            if (candidate >= minValue && candidate != answer) {
                wrongSet.add(candidate);
                if (wrongSet.size() == 3) {
                    break;
                }
            }
        }
        int delta = 1;
        while (wrongSet.size() < 3) {
            int up = answer + delta;
            if (up >= minValue && up != answer) {
                wrongSet.add(up);
            }
            if (wrongSet.size() < 3) {
                int down = answer - delta;
                if (down >= minValue && down <= maxValue && down != answer) {
                    wrongSet.add(down);
                }
            }
            delta++;
        }
        List<String> distractors = wrongSet.stream().limit(3).map(String::valueOf).toList();
        return new GeneratedMathQuestion(
                "math:" + grade.name() + ":" + keySuffix,
                questionText,
                String.valueOf(answer),
                distractors,
                explanation,
                grade
        );
    }

    private java.util.Random toUtilRandom() {
        return new java.util.Random(random.nextLong());
    }

    public record GeneratedMathQuestion(
            String questionKey,
            String questionText,
            String correctText,
            List<String> distractors,
            String explanation,
            QuizGrade grade
    ) {
    }
}
