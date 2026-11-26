package com.dippie.service.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// test migration Flyway V10 khớp bản database/migrations/20 và script H2 chuyển math_level sang quiz_grade an toàn khi chạy lặp lại
class QuizSchemaMigrationTest {

    @Test
    void includesFlywayV10SyncedWithDatabaseMigrationsAndRequiredTables() throws Exception {
        ClassPathResource v10 = new ClassPathResource("db/migration/V10__add_quiz_bank.sql");
        assertThat(v10.exists()).isTrue();
        String v10Sql = v10.getContentAsString(StandardCharsets.UTF_8);

        assertThat(v10Sql).contains("quiz_grade");
        assertThat(v10Sql).contains("MAU_GIAO");
        assertThat(v10Sql).contains("LOP_5");
        assertThat(v10Sql).contains("DROP COLUMN math_level");
        assertThat(v10Sql).contains("CREATE TABLE IF NOT EXISTS quiz_sets");
        assertThat(v10Sql).contains("CREATE TABLE IF NOT EXISTS quiz_questions");
        assertThat(v10Sql).contains("CREATE TABLE IF NOT EXISTS child_quiz_sets");
        assertThat(v10Sql).contains("CREATE TABLE IF NOT EXISTS quiz_attempts");

        Path repoMigration = Path.of("..", "..", "database", "migrations", "20_add_quiz_bank.sql").normalize();
        assertThat(Files.exists(repoMigration)).isTrue();
        assertThat(Files.readString(repoMigration, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .isEqualTo(v10Sql.replace("\r\n", "\n"));
    }

    @Test
    void upgradesH2DatabaseFromLegacyMathLevelsIdempotently() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:quiz-bank-upgrade;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    create table users (
                        id bigint auto_increment primary key,
                        username varchar(50) not null
                    )
                    """);
            statement.execute("""
                    create table child_profiles (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        name varchar(50) not null,
                        avatar_key varchar(32) not null,
                        daily_minutes int default 60,
                        math_level tinyint not null default 1,
                        reward_minutes tinyint not null default 5,
                        max_bonus_minutes smallint not null default 30,
                        created_at timestamp not null default current_timestamp,
                        updated_at timestamp not null default current_timestamp
                    )
                    """);
            statement.execute("insert into users (id, username) values (1, 'mom')");
            statement.execute("insert into child_profiles (user_id, name, avatar_key, math_level) values (1, 'Bé Mầm', 'bear', 1)");
            statement.execute("insert into child_profiles (user_id, name, avatar_key, math_level) values (1, 'Bé Một', 'bunny', 2)");
            statement.execute("insert into child_profiles (user_id, name, avatar_key, math_level) values (1, 'Bé Hai', 'cat', 3)");
            statement.execute("insert into child_profiles (user_id, name, avatar_key, math_level) values (1, 'Bé Ba', 'dino', 4)");

            ClassPathResource h2Script = new ClassPathResource("quiz-bank-h2.sql");
            ScriptUtils.executeSqlScript(connection, h2Script);
            ScriptUtils.executeSqlScript(connection, h2Script);

            Map<String, String> gradesByName = new LinkedHashMap<>();
            try (var rows = statement.executeQuery("select name, quiz_grade from child_profiles order by id asc")) {
                while (rows.next()) {
                    gradesByName.put(rows.getString(1), rows.getString(2));
                }
            }

            assertThat(gradesByName).containsExactly(
                    Map.entry("Bé Mầm", "MAU_GIAO"),
                    Map.entry("Bé Một", "LOP_1"),
                    Map.entry("Bé Hai", "LOP_2"),
                    Map.entry("Bé Ba", "LOP_3")
            );

            // kiểm tra cột math_level cũ đã được gỡ bỏ và bảng mới hoạt động bình thường
            try (var cols = statement.executeQuery(
                    "select column_name from information_schema.columns where upper(table_name) = 'CHILD_PROFILES' and upper(column_name) = 'MATH_LEVEL'")) {
                assertThat(cols.next()).isFalse();
            }

            statement.execute("""
                    insert into quiz_sets (owner_user_id, name, default_subject, default_grade, built_in, dynamic_generator, active)
                    values (1, 'Ôn tập tuần 5', 'TIENG_VIET', 'LOP_3', false, false, true)
                    """);
            try (var countRows = statement.executeQuery("select count(*) from quiz_sets")) {
                assertThat(countRows.next()).isTrue();
                assertThat(countRows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
