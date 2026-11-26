package com.dippie.service.profile;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

// test file Flyway V7 có đủ cột giới hạn thời gian + bảng sổ cái và script H2 demo chạy lặp lại vẫn giữ hồ sơ cũ
class ScreenTimeSchemaMigrationTest {

    @Test
    void includesV7FlywayMigrationWithBudgetColumnsAndScreenTimeLedger() throws Exception {
        ClassPathResource v7 = new ClassPathResource("db/migration/V7__add_screen_time_and_math.sql");
        assertThat(v7.exists()).isTrue();
        String sql = v7.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("daily_minutes");
        assertThat(sql).contains("math_level");
        assertThat(sql).contains("reward_minutes");
        assertThat(sql).contains("max_bonus_minutes");
        assertThat(sql).contains("ck_child_profile_daily_minutes");
        assertThat(sql).contains("ck_child_profile_math_level");
        assertThat(sql).contains("ck_child_profile_reward_minutes");
        assertThat(sql).contains("ck_child_profile_max_bonus_minutes");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS screen_time_ledger");
        assertThat(sql).contains("fk_screen_time_ledger_profile");
        assertThat(sql).contains("idx_screen_time_ledger_profile_day");
        assertThat(sql).contains("PARENT_GRANT");
    }

    @Test
    void upgradesExistingDemoH2DatabaseIdempotentlyWithoutLosingChildProfiles() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:screen-time-upgrade;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    create table child_profiles (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        name varchar(50) not null,
                        avatar_key varchar(32) not null,
                        created_at timestamp not null default current_timestamp,
                        updated_at timestamp not null default current_timestamp
                    )
                    """);
            statement.execute("insert into child_profiles (user_id, name, avatar_key) values (1, 'Bé Bi', 'bear')");

            ClassPathResource migration = new ClassPathResource("screen-time-h2.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            try (var rows = statement.executeQuery(
                    "select id, daily_minutes, math_level, reward_minutes, max_bonus_minutes from child_profiles")) {
                assertThat(rows.next()).isTrue();
                long profileId = rows.getLong(1);
                assertThat(rows.getInt(2)).isEqualTo(60);
                assertThat(rows.getInt(3)).isEqualTo(1);
                assertThat(rows.getInt(4)).isEqualTo(5);
                assertThat(rows.getInt(5)).isEqualTo(30);

                statement.execute("insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values ("
                        + profileId + ", date '2026-11-26', 'WATCH', 15)");
            }

            try (var countRows = statement.executeQuery("select count(*) from screen_time_ledger")) {
                assertThat(countRows.next()).isTrue();
                assertThat(countRows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
