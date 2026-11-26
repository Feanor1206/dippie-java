package com.dippie.service.play;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test Flyway V9, bản migration đồng bộ và script H2 demo chạy lặp lại không làm mất dữ liệu cũ
class PlaySchemaMigrationTest {

    @Test
    void includesV9FlywayMigrationAndDatabaseMigrationInSync() throws Exception {
        ClassPathResource v9 = new ClassPathResource("db/migration/V9__add_kid_play_features.sql");
        assertThat(v9.exists()).isTrue();
        String sql = v9.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("ck_screen_time_ledger_kind");
        assertThat(sql).contains("BOUNTY");
        assertThat(sql).contains("LOOT");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_play_settings");
        assertThat(sql).contains("ck_child_play_pet_faint_minutes");
        assertThat(sql).contains("ck_child_play_pet_rest_minutes");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_play_state");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_episode_recap_gates");
        assertThat(sql).contains("uq_recap_gate_profile_target");

        Path manualMigration = Path.of("..", "..", "database", "migrations", "19_add_kid_play_features.sql").normalize();
        assertThat(Files.exists(manualMigration)).isTrue();
        String manualSql = Files.readString(manualMigration, StandardCharsets.UTF_8);
        assertThat(manualSql).contains("child_play_settings");
        assertThat(manualSql).contains("child_play_state");
        assertThat(manualSql).contains("child_episode_recap_gates");
        assertThat(manualSql).contains("BOUNTY");
        assertThat(manualSql).contains("LOOT");
    }

    @Test
    void upgradesDemoH2DatabaseIdempotentlyWithoutLosingExistingProfiles() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:play-features-upgrade;MODE=MySQL", "sa", "");
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
            statement.execute("create table catalog_series (id varchar(100) primary key)");
            statement.execute("create table catalog_episodes (id varchar(100) primary key)");
            statement.execute("insert into users (username) values ('parent')");
            statement.execute("insert into child_profiles (user_id, name, avatar_key) values (1, 'Bé Mây', 'bunny')");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("screen-time-h2.sql"));
            ClassPathResource parentMigration = new ClassPathResource("parent-features-h2.sql");
            ClassPathResource playMigration = new ClassPathResource("play-features-h2.sql");
            ScriptUtils.executeSqlScript(connection, parentMigration);
            ScriptUtils.executeSqlScript(connection, playMigration);
            ScriptUtils.executeSqlScript(connection, parentMigration);
            ScriptUtils.executeSqlScript(connection, playMigration);

            long profileId;
            try (var rows = statement.executeQuery("select id from child_profiles")) {
                assertThat(rows.next()).isTrue();
                profileId = rows.getLong(1);
            }

            statement.execute("insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values ("
                    + profileId + ", date '2026-11-26', 'BOUNTY', 300)");
            statement.execute("insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values ("
                    + profileId + ", date '2026-11-26', 'LOOT', 180)");
            statement.execute("insert into child_play_settings (profile_id) values (" + profileId + ")");
            statement.execute("insert into child_play_state (profile_id) values (" + profileId + ")");
            statement.execute("insert into child_episode_recap_gates (profile_id, series_id, source_episode_id, target_episode_id) values ("
                    + profileId + ", 'ser_1', 'ep_1', 'ep_2')");

            try (var settingsRows = statement.executeQuery(
                    "select watch_pet_enabled, watch_pet_faint_minutes, watch_pet_rest_minutes, loot_drop_enabled, recap_to_next_enabled from child_play_settings")) {
                assertThat(settingsRows.next()).isTrue();
                assertThat(settingsRows.getBoolean(1)).isTrue();
                assertThat(settingsRows.getInt(2)).isEqualTo(20);
                assertThat(settingsRows.getInt(3)).isEqualTo(5);
                assertThat(settingsRows.getBoolean(4)).isTrue();
                assertThat(settingsRows.getBoolean(5)).isTrue();
            }
        }
    }

    @Test
    void reconcilesLegacyH2EnumAndAutoCheckConstraintSoLootLedgerInsertDoesNotFailWith500() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:legacy-loot-ledger;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    create table child_profiles (
                        id bigint auto_increment primary key,
                        name varchar(50) not null
                    )
                    """);
            statement.execute("insert into child_profiles (name) values ('Bé Bi')");
            // tái hiện bảng screen_time_ledger cũ do hibernate tạo trước khi có hằng số loot
            statement.execute("""
                    create table screen_time_ledger (
                        id bigint auto_increment primary key,
                        profile_id bigint not null,
                        usage_day date not null,
                        kind enum('WATCH', 'REWARD', 'PARENT_GRANT', 'BOUNTY') not null,
                        seconds int not null,
                        created_at timestamp not null default current_timestamp,
                        constraint constraint_legacy_kind check (kind in ('WATCH', 'REWARD', 'PARENT_GRANT', 'BOUNTY'))
                    )
                    """);

            assertThatThrownBy(() -> statement.execute(
                    "insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values (1, date '2026-11-26', 'LOOT', 180)"
            )).isInstanceOf(SQLException.class);

            ScreenTimeLedgerSchemaReconciler.reconcileH2Connection(connection);

            statement.execute(
                    "insert into screen_time_ledger (profile_id, usage_day, kind, seconds) values (1, date '2026-11-26', 'LOOT', 180)"
            );
            try (var rows = statement.executeQuery("select kind, seconds from screen_time_ledger where kind = 'LOOT'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("LOOT");
                assertThat(rows.getInt(2)).isEqualTo(180);
            }
        }
    }
}
