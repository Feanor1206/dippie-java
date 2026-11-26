package com.dippie.service.movie;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

// Test script migration catalog-year-h2.sql chạy lặp lại hai lần trên DB H2 demo cũ vẫn an toàn và cho phép release_year bằng null.
class MovieYearH2MigrationTest {

    @Test
    void allowsUnknownYearsInAnExistingDemoDatabase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:old-movies;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("create table movies (release_year int not null)");
            statement.execute("insert into movies values (2004)");

            var migration = new ClassPathResource("catalog-year-h2.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            statement.execute("insert into movies values (null)");

            try (var rows = statement.executeQuery("select count(*), count(release_year) from movies")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.getInt(2)).isEqualTo(1);
            }
        }
    }
}
