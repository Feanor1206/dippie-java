package com.dippie.service.progress;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

// chạy watch-progress-h2.sql 2 lần trên H2 cũ để gỡ khóa ngoại sang catalog_media_assets cho lưu mã youtube:, khỏi mất dữ liệu cũ
class WatchProgressH2MigrationTest {

    @Test
    void upgradesAnExistingDemoDatabaseWithoutLosingFileProgress() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:old-progress;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("create table catalog_media_assets (id varchar(100) primary key)");
            statement.execute("""
                    create table watch_progress (
                        asset_id varchar(100) not null,
                        constraint fk_watch_progress_asset foreign key (asset_id) references catalog_media_assets(id)
                    )
                    """);
            statement.execute("insert into catalog_media_assets values ('file')");
            statement.execute("insert into watch_progress values ('file')");

            var migration = new ClassPathResource("watch-progress-h2.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            statement.execute("insert into watch_progress values ('youtube:2ykw6ZxTb8o')");

            try (var rows = statement.executeQuery("select count(*) from watch_progress")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
