package com.dippie.desktop.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;

// factory chọn cấu hình DB lúc mở app, có file desktop.properties thì dùng MySQL còn không thì trỏ về H2 demo
public final class DatabaseFactory {
    private DatabaseFactory() {
    }

    // chỉ rơi về H2 demo khi chưa có desktop.properties, có file rồi mà hỏng thì báo lỗi luôn để sửa
    public static DatabaseConfig load(Path moduleDirectory) {
        Path module = moduleDirectory.toAbsolutePath().normalize();
        Path configFile = module.resolve("desktop.properties");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException exception) {
            return defaultH2Config(module.getParent());
        } catch (IOException | IllegalArgumentException exception) {
            throw new DataAccessException.Connection("Không đọc được tệp desktop.properties.", exception);
        }

        String url = properties.getProperty("url", "").strip();
        String user = properties.getProperty("user", "").strip();
        if (!url.startsWith("jdbc:mysql://") || user.isBlank() || !properties.containsKey("password")) {
            throw new DataAccessException.Connection(
                    "Tệp desktop.properties cần có url bắt đầu bằng jdbc:mysql://, kèm user và password.", null);
        }
        return new DatabaseConfig(url, user, properties.getProperty("password"));
    }

    // cố tình bật IFEXISTS=TRUE nha, bỏ đi là H2 tự đẻ 1 DB rỗng sai chỗ thay vì báo thiếu dữ liệu demo
    private static DatabaseConfig defaultH2Config(Path repoRoot) {
        String dbPath = repoRoot.resolve("target/dippie-java/dippie").toString()
                .replace('\\', '/').replace(";", "\\;");
        String url = "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE;IFEXISTS=TRUE";
        return new DatabaseConfig(url, "sa", "");
    }
}
