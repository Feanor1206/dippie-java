package com.dippie.model.profile;

import com.dippie.exception.InvalidRequestException;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test sinh đề toán 4 mức độ và công thức tính giờ xem qua mốc 0h múi giờ Asia/Ho_Chi_Minh
class MathChallengeGeneratorTest {

    @Test
    void generatesValidQuestionsAcrossAllFourLevels() {
        MathChallengeGenerator generator = new MathChallengeGenerator(new Random(20261126L));

        for (int level = 1; level <= 4; level++) {
            boolean sawFirstOp = false;
            boolean sawSecondOp = false;
            for (int i = 0; i < 80; i++) {
                MathChallengeGenerator.MathQuestion item = generator.generate(level);
                assertThat(item.level()).isEqualTo(level);
                assertThat(item.question()).endsWith(" = ?");

                String[] parts = item.question().replace(" = ?", "").split(" ");
                assertThat(parts).hasSize(3);
                int left = Integer.parseInt(parts[0]);
                String op = parts[1];
                int right = Integer.parseInt(parts[2]);

                if (level <= 3) {
                    int max = level == 1 ? 10 : (level == 2 ? 20 : 100);
                    if ("+".equals(op)) {
                        sawFirstOp = true;
                        assertThat(left + right).isEqualTo(item.answer()).isBetween(2, max);
                    } else {
                        sawSecondOp = true;
                        assertThat(op).isEqualTo("-");
                        assertThat(left).isLessThanOrEqualTo(max);
                        assertThat(left - right).isEqualTo(item.answer()).isGreaterThan(0);
                    }
                } else {
                    if ("×".equals(op)) {
                        sawFirstOp = true;
                        assertThat(left).isBetween(2, 9);
                        assertThat(right).isBetween(2, 9);
                        assertThat(left * right).isEqualTo(item.answer());
                    } else {
                        sawSecondOp = true;
                        assertThat(op).isEqualTo("÷");
                        assertThat(right).isBetween(2, 9);
                        assertThat(left % right).isZero();
                        assertThat(left / right).isEqualTo(item.answer()).isBetween(2, 9);
                    }
                }
            }
            assertThat(sawFirstOp).as("level " + level + " first op").isTrue();
            assertThat(sawSecondOp).as("level " + level + " second op").isTrue();
        }
    }

    @Test
    void rejectsOutOfRangeMathLevels() {
        MathChallengeGenerator generator = new MathChallengeGenerator(new Random(1L));
        assertThatThrownBy(() -> generator.generate(0)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> generator.generate(5)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void computesVietnamDayBoundaryAndSummaryBalances() {
        // 16:59:59Z là 23:59:59+07:00 cùng ngày, còn 17:00:00Z sang 00:00:00+07:00 ngày hôm sau
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-11-26T16:59:59Z"), ZoneOffset.UTC);
        Clock atMidnight = Clock.fixed(Instant.parse("2026-11-26T17:00:00Z"), ZoneOffset.UTC);

        assertThat(ScreenTimeSummary.todayVietnam(beforeMidnight)).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(ScreenTimeSummary.todayVietnam(atMidnight)).isEqualTo(LocalDate.of(2026, 11, 27));

        ChildProfile profile = new ChildProfile(1L, "Bé Mây", "star", LocalDateTime.of(2026, 11, 26, 10, 0));
        profile.updateScreenTimeSettings(30, 2, 5, 15, LocalDateTime.of(2026, 11, 26, 10, 5));

        ScreenTimeSummary mid = ScreenTimeSummary.of(profile, LocalDate.of(2026, 11, 26), 1200, 600, 900);
        assertThat(mid.dailySeconds()).isEqualTo(1800L);
        assertThat(mid.remainingSeconds()).isEqualTo(2100L);
        assertThat(mid.locked()).isFalse();
        assertThat(mid.bonusRemainingSeconds()).isEqualTo(300L);
        assertThat(mid.bonusCapReached()).isFalse();

        ScreenTimeSummary exhausted = ScreenTimeSummary.of(profile, LocalDate.of(2026, 11, 26), 4000, 900, 0);
        assertThat(exhausted.remainingSeconds()).isZero();
        assertThat(exhausted.locked()).isTrue();
        assertThat(exhausted.bonusRemainingSeconds()).isZero();
        assertThat(exhausted.bonusCapReached()).isTrue();
    }
}
