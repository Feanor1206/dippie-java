package com.dippie.service.profile;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

// test file Flyway V6 có đủ bảng cột với script nâng cấp H2 demo chạy lặp lại vẫn giữ dữ liệu cũ
class ChildProfileSchemaMigrationTest {

    @Test
    void includesV6FlywayMigrationWithChildProfilesAndScopedColumns() throws Exception {
        ClassPathResource v6 = new ClassPathResource("db/migration/V6__add_child_profiles.sql");
        assertThat(v6.exists()).isTrue();
        String sql = v6.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("parent_mode_enabled");
        assertThat(sql).contains("parent_pin_hash");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS child_profiles");
        assertThat(sql).contains("uq_user_profile_movie_watchlist");
        assertThat(sql).contains("uq_user_profile_movie_favorite");
        assertThat(sql).contains("uq_user_profile_movie_rating");
        assertThat(sql).contains("uq_watch_progress_user_profile_episode");
        assertThat(sql).contains("fk_watchlist_profile");
        assertThat(sql).contains("fk_favorite_profile");
        assertThat(sql).contains("fk_rating_profile");
        assertThat(sql).contains("fk_watch_progress_profile");
    }

    @Test
    void upgradesExistingDemoH2DatabaseIdempotentlyWithoutLosingRows() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:child-profile-upgrade;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    create table watchlist_items (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        profile_id bigint null,
                        profile_scope_id bigint not null default 0,
                        movie_id varchar(100) not null,
                        constraint uq_user_movie_watchlist unique (user_id, movie_id),
                        constraint uq_user_profile_movie_watchlist unique (user_id, profile_scope_id, movie_id)
                    )
                    """);
            statement.execute("""
                    create table favorite_items (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        profile_id bigint null,
                        profile_scope_id bigint not null default 0,
                        movie_id varchar(100) not null,
                        constraint uq_user_movie_favorite unique (user_id, movie_id),
                        constraint uq_user_profile_movie_favorite unique (user_id, profile_scope_id, movie_id)
                    )
                    """);
            statement.execute("""
                    create table movie_ratings (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        profile_id bigint null,
                        profile_scope_id bigint not null default 0,
                        movie_id varchar(100) not null,
                        rating int not null,
                        constraint uq_user_movie_rating unique (user_id, movie_id),
                        constraint uq_user_profile_movie_rating unique (user_id, profile_scope_id, movie_id)
                    )
                    """);
            statement.execute("""
                    create table watch_progress (
                        id bigint auto_increment primary key,
                        user_id bigint not null,
                        profile_id bigint null,
                        profile_scope_id bigint not null default 0,
                        episode_id varchar(100) not null,
                        constraint uq_watch_progress_user_episode unique (user_id, episode_id),
                        constraint uq_watch_progress_user_profile_episode unique (user_id, profile_scope_id, episode_id)
                    )
                    """);
            statement.execute("insert into watchlist_items (user_id, movie_id) values (1, 'mov_1')");
            statement.execute("insert into favorite_items (user_id, movie_id) values (1, 'mov_1')");
            statement.execute("insert into movie_ratings (user_id, movie_id, rating) values (1, 'mov_1', 9)");
            statement.execute("insert into watch_progress (user_id, episode_id) values (1, 'ep_1')");

            ClassPathResource migration = new ClassPathResource("child-profiles-h2.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            // sau khi gỡ unique 2 cột cũ thì hồ sơ bé thêm cùng phim và tập phải chèn trơn tru
            statement.execute("insert into watchlist_items (user_id, profile_id, profile_scope_id, movie_id) values (1, 10, 10, 'mov_1')");
            statement.execute("insert into favorite_items (user_id, profile_id, profile_scope_id, movie_id) values (1, 10, 10, 'mov_1')");
            statement.execute("insert into movie_ratings (user_id, profile_id, profile_scope_id, movie_id, rating) values (1, 10, 10, 'mov_1', 7)");
            statement.execute("insert into watch_progress (user_id, profile_id, profile_scope_id, episode_id) values (1, 10, 10, 'ep_1')");

            for (String table : new String[]{"watchlist_items", "favorite_items", "movie_ratings", "watch_progress"}) {
                try (var rows = statement.executeQuery("select count(*) from " + table)) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            }
        }
    }
}
