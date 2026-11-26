package com.dippie.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// check cấu hình Flyway theo từng profile với đủ file migration từ V1 tới V5
class FlywayConfigurationTest {

    // demo với test tắt Flyway, còn mysql với prod bật kèm baselineOnMigrate
    @Test
    void enablesFlywayWithBaselineOnMigrateForMysqlAndProdWhileKeepingDemoOff() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> mainDocs = loader.load("application", new ClassPathResource("application.yml"));
        List<PropertySource<?>> prodDocs = loader.load("prod", new ClassPathResource("application-prod.yml"));

        PropertySource<?> baseDoc = mainDocs.get(0);
        PropertySource<?> mysqlDoc = mainDocs.stream()
                .filter(source -> "mysql".equals(source.getProperty("spring.config.activate.on-profile")))
                .findFirst()
                .orElseThrow();
        PropertySource<?> prodDoc = prodDocs.getFirst();

        assertThat(baseDoc.getProperty("spring.flyway.enabled")).isEqualTo(false);

        assertThat(mysqlDoc.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(mysqlDoc.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(true);
        assertThat(String.valueOf(mysqlDoc.getProperty("spring.flyway.baseline-version"))).isEqualTo("1");
        assertThat(mysqlDoc.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");

        assertThat(prodDoc.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(prodDoc.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(true);
        assertThat(String.valueOf(prodDoc.getProperty("spring.flyway.baseline-version"))).isEqualTo("1");
        assertThat(prodDoc.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
    }

    // chắc chắn 5 file V1 tới V5 trên classpath có đủ bảng, cột với index chính
    @Test
    void includesVersionedFlywayMigrationsOnClasspath() throws Exception {
        String v1 = readClasspath("db/migration/V1__baseline_schema.sql");
        String v2 = readClasspath("db/migration/V2__add_user_terms_accepted_at.sql");
        String v3 = readClasspath("db/migration/V3__add_playback_reports.sql");
        String v4 = readClasspath("db/migration/V4__add_password_reset_tokens.sql");
        String v5 = readClasspath("db/migration/V5__add_query_performance_indexes.sql");

        assertThat(v1).contains("CREATE TABLE IF NOT EXISTS users");
        assertThat(v1).contains("CREATE TABLE IF NOT EXISTS admin_audit_events");
        assertThat(v1).contains("release_year INT NULL");
        assertThat(v1).doesNotContain("fk_watch_progress_asset");

        assertThat(v2).contains("terms_accepted_at");
        assertThat(v3).contains("CREATE TABLE IF NOT EXISTS playback_reports");
        assertThat(v4).contains("CREATE TABLE IF NOT EXISTS password_reset_tokens");
        assertThat(v5).contains("idx_users_role_enabled");
        assertThat(v5).contains("idx_cat_ep_series_pub_num");
    }

    private static String readClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        assertThat(resource.exists()).as("Thiếu file migration Flyway: " + path).isTrue();
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
