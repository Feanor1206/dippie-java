package com.dippie.model.quiz;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// test bộ sinh toán trắc nghiệm 4 đáp án theo lớp, không ra số âm, lớp 3 đúng bảng cửu chương 2-9 và kho câu sẵn có
class MathQuestionGeneratorTest {

    @Test
    void generatesFourDistinctNonNegativeOptionsAcrossAllGrades() {
        MathQuestionGenerator generator = new MathQuestionGenerator(new Random(20261126L));

        for (QuizGrade grade : QuizGrade.values()) {
            for (int i = 0; i < 80; i++) {
                MathQuestionGenerator.GeneratedMathQuestion q = generator.generate(grade);
                assertThat(q.grade()).isEqualTo(grade);
                assertThat(q.questionText()).isNotBlank();
                assertThat(q.explanation()).isNotBlank();
                assertThat(q.distractors()).hasSize(3);

                int correct = Integer.parseInt(q.correctText());
                assertThat(correct).isGreaterThanOrEqualTo(0);

                Set<String> unique = new HashSet<>();
                unique.add(q.correctText());
                for (String distractor : q.distractors()) {
                    int val = Integer.parseInt(distractor);
                    assertThat(val).isGreaterThanOrEqualTo(0);
                    assertThat(unique.add(distractor)).isTrue();
                }
                assertThat(unique).hasSize(4);

                if (grade == QuizGrade.MAU_GIAO) {
                    assertThat(correct).isBetween(0, 10);
                } else if (grade == QuizGrade.LOP_1) {
                    assertThat(correct).isBetween(0, 20);
                } else if (grade == QuizGrade.LOP_2) {
                    assertThat(correct).isBetween(0, 100);
                }
            }
        }
    }

    @Test
    void gradeThreeUsesMultiplicationAndDivisionTablesTwoToNine() {
        MathQuestionGenerator generator = new MathQuestionGenerator(new Random(777L));
        boolean sawMultiply = false;
        boolean sawDivide = false;

        for (int i = 0; i < 120; i++) {
            MathQuestionGenerator.GeneratedMathQuestion q = generator.generate(QuizGrade.LOP_3);
            String expr = q.questionText().replace(" = ?", "");
            String[] parts = expr.split(" ");
            assertThat(parts).hasSize(3);
            int left = Integer.parseInt(parts[0]);
            String op = parts[1];
            int right = Integer.parseInt(parts[2]);
            int answer = Integer.parseInt(q.correctText());

            if ("×".equals(op)) {
                sawMultiply = true;
                assertThat(left).isBetween(2, 9);
                assertThat(right).isBetween(2, 9);
                assertThat(answer).isEqualTo(left * right);
            } else if ("÷".equals(op)) {
                sawDivide = true;
                assertThat(right).isBetween(2, 9);
                assertThat(answer).isBetween(2, 9);
                assertThat(left).isEqualTo(right * answer);
            } else {
                throw new AssertionError("lớp 3 chỉ được dùng nhân hoặc chia, gặp: " + q.questionText());
            }
        }

        assertThat(sawMultiply).isTrue();
        assertThat(sawDivide).isTrue();
    }

    @Test
    void avoidsRecentlyAskedKeysWhenPoolAllows() {
        MathQuestionGenerator generator = new MathQuestionGenerator(new Random(42L));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            MathQuestionGenerator.GeneratedMathQuestion q = generator.generateAvoiding(QuizGrade.LOP_3, seen);
            assertThat(seen.add(q.questionKey())).isTrue();
        }
    }

    @Test
    void mapsLegacyMathLevelsAndGradeTransitionsCleanly() {
        assertThat(QuizGrade.fromLegacyMathLevel(1)).isEqualTo(QuizGrade.MAU_GIAO);
        assertThat(QuizGrade.fromLegacyMathLevel(2)).isEqualTo(QuizGrade.LOP_1);
        assertThat(QuizGrade.fromLegacyMathLevel(3)).isEqualTo(QuizGrade.LOP_2);
        assertThat(QuizGrade.fromLegacyMathLevel(4)).isEqualTo(QuizGrade.LOP_3);
        assertThat(QuizGrade.fromLegacyMathLevel(99)).isEqualTo(QuizGrade.LOP_1);

        assertThat(QuizGrade.MAU_GIAO.hasPrevious()).isFalse();
        assertThat(QuizGrade.MAU_GIAO.previousGrade()).isEqualTo(QuizGrade.MAU_GIAO);
        assertThat(QuizGrade.MAU_GIAO.nextGrade()).isEqualTo(QuizGrade.LOP_1);
        assertThat(QuizGrade.LOP_5.hasNext()).isFalse();
        assertThat(QuizGrade.LOP_5.nextGrade()).isEqualTo(QuizGrade.LOP_5);
        assertThat(QuizGrade.LOP_5.previousGrade()).isEqualTo(QuizGrade.LOP_4);
    }

    @Test
    void builtInNonMathCatalogShipsThirtyEnglishAndThirtyScienceRiddleQuestionsAcrossAllGrades() {
        List<BuiltInQuizCatalog.SeedQuestion> english = BuiltInQuizCatalog.englishQuestions();
        List<BuiltInQuizCatalog.SeedQuestion> scienceRiddles = BuiltInQuizCatalog.scienceAndRiddleQuestions();

        assertThat(english).hasSize(30);
        assertThat(scienceRiddles).hasSize(30);

        for (QuizGrade grade : QuizGrade.values()) {
            assertThat(english.stream().filter(q -> q.grade() == grade).count()).isGreaterThanOrEqualTo(5);
            assertThat(scienceRiddles.stream().filter(q -> q.grade() == grade).count()).isGreaterThanOrEqualTo(5);
        }

        Set<String> allKeys = new HashSet<>();
        for (BuiltInQuizCatalog.SeedQuestion q : english) {
            assertThat(allKeys.add(q.seedKey())).isTrue();
            assertThat(q.subject()).isEqualTo(QuizSubject.TIENG_ANH);
            assertValidSeedQuestion(q);
        }
        for (BuiltInQuizCatalog.SeedQuestion q : scienceRiddles) {
            assertThat(allKeys.add(q.seedKey())).isTrue();
            assertThat(q.subject()).isIn(QuizSubject.KHOA_HOC, QuizSubject.DO_VUI);
            assertValidSeedQuestion(q);
        }
    }

    private static void assertValidSeedQuestion(BuiltInQuizCatalog.SeedQuestion q) {
        assertThat(q.questionText()).isNotBlank();
        assertThat(q.explanation()).isNotBlank();
        Set<String> options = Set.of(
                q.optionA().trim().toLowerCase(),
                q.optionB().trim().toLowerCase(),
                q.optionC().trim().toLowerCase(),
                q.optionD().trim().toLowerCase()
        );
        assertThat(options).hasSize(4);
    }
}
