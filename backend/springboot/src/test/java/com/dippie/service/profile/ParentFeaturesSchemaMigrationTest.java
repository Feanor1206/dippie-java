package com.dippie.service.profile;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

// test file Flyway V8 khớp bản database/migrations/18 và script H2 demo chạy lặp lại vẫn nâng cấp được bảng cũ
class ParentFeaturesSchemaMigrationTest {

    @Test
    void includesV8FlywayMigrationSyncedWithDatabaseMigrationsFolder() throws Exception {
        ClassPathResource v8 = new ClassPathResource("db/migration/V8__add_parent_features.sql");
        assertThat(v8.exists()).isTrue();
        String sql = v8.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("MODIFY COLUMN daily_minutes INT NULL DEFAULT 60");
        assertThat(sql).contains("'BOUNTY'");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS bounty_tasks");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS bounty_task_profiles");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS bounty_submissions");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS weekend_vault_overrides");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_vip_series");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_vip_episode_unlocks");

        Path repoCopy = Path.of("..", "..", "database", "migrations", "18_add_parent_features.sql").normalize();
        assertThat(Files.exists(repoCopy)).isTrue();
        assertThat(Files.readString(repoCopy, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .isEqualTo(sql.replace("\r\n", "\n"));
    }

    @Test
    void upgradesExistingDemoH2DatabaseIdempotentlyForUnlimitedAndParentFeatures() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:parent-features-upgrade;MODE=MySQL", "sa", "");
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
                        created_at timestamp not null default current_timestamp,
                        updated_at timestamp not null default current_timestamp
                    )
                    """);
            statement.execute("""
                    create table catalog_series (
                        id varchar(100) primary key
                    )
                    """);
            statement.execute("""
                    create table catalog_episodes (
                        id varchar(100) primary key
                    )
                    """);
            statement.execute("insert into users (username) values ('parent')");
            statement.execute("insert into child_profiles (user_id, name, avatar_key) values (1, 'Bé Mây', 'bunny')");
            statement.execute("insert into catalog_series (id) values ('ser_vip')");
            statement.execute("insert into catalog_episodes (id) values ('ep_vip_1')");

            // chạy script V7 cũ trước rồi chạy lặp lại script V8 để chắc chắn cột daily_minutes cho phép NULL
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("screen-time-h2.sql"));
            ClassPathResource v8H2 = new ClassPathResource("parent-features-h2.sql");
            ScriptUtils.executeSqlScript(connection, v8H2);
            ScriptUtils.executeSqlScript(connection, v8H2);

            try (var update = connection.prepareStatement("update child_profiles set daily_minutes = ? where id = 1")) {
                update.setNull(1, Types.INTEGER);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("select daily_minutes from child_profiles where id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isNull();
            }

            statement.execute("insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values (1, date '2026-11-26', 'BOUNTY', 600)");
            statement.execute("insert into bounty_tasks (user_id, title, reward_minutes, active, repeatable_daily) values (1, 'Dọn đồ chơi', 15, true, true)");
            statement.execute("insert into bounty_task_profiles (task_id, profile_id) values (1, 1)");
            statement.execute("insert into bounty_submissions (task_id, profile_id, usage_day, status, reward_minutes) values (1, 1, date '2026-11-26', 'PENDING', 15)");
            statement.execute("insert into weekend_vault_overrides (profile_id, week_start, unlocked) values (1, date '2026-11-23', true)");
            statement.execute("insert into child_vip_series (profile_id, series_id) values (1, 'ser_vip')");
            statement.execute("insert into child_vip_episode_unlocks (profile_id, episode_id, unlock_day) values (1, 'ep_vip_1', date '2026-11-26')");

            try (var check = statement.executeQuery("select count(*) from bounty_submissions where status = 'PENDING'")) {
                assertThat(check.next()).isTrue();
                assertThat(check.getInt(1)).isEqualTo(1);
            }
        }
    }
}
