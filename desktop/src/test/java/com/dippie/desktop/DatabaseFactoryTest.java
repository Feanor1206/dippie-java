package com.dippie.desktop;

import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.db.DatabaseFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Test chọn cấu hình H2 mặc định và đọc tệp desktop.properties mà không cần mở DB thật.
class DatabaseFactoryTest {
    @TempDir
    Path directory;

    @Test
    void defaultPointsToExistingDemoFileUnderTheRepositoryRoot() throws Exception {
        Path module = Files.createDirectory(directory.resolve("desktop"));
        var config = DatabaseFactory.load(module);
        String path = directory.toString().replace('\\', '/');
        assertEquals("jdbc:h2:file:" + path + "/target/dippie-java/dippie;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE;IFEXISTS=TRUE",
                config.url());
        assertEquals("sa", config.user());
        assertEquals("", config.password());
        assertFalse(Files.exists(directory.resolve("target")));
    }

    @Test
    void readsMySqlCredentialsAndRejectsAnUnsupportedUrl() throws Exception {
        Path configFile = directory.resolve("desktop.properties");
        Files.writeString(configFile, "url=jdbc:mysql://localhost:3306/dippie\nuser=demo\npassword=tiếng Việt\n");
        var config = DatabaseFactory.load(directory);
        assertEquals("jdbc:mysql://localhost:3306/dippie", config.url());
        assertEquals("demo", config.user());
        assertEquals("tiếng Việt", config.password());

        Files.writeString(configFile, "url=jdbc:other:test\n");
        assertThrows(DataAccessException.Connection.class, () -> DatabaseFactory.load(directory));
    }
}
