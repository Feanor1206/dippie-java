package com.dippie.desktop;

import com.dippie.desktop.db.DatabaseConfig;
import com.dippie.desktop.db.JdbcDatabase;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;

// lớp cha cho mấy bài test JDBC, mỗi test tạo 1 DB H2 riêng trong RAM rồi nạp lại schema và fixtures
abstract class TestDatabase {
    protected JdbcDatabase db;
    // H2 in-memory đóng kết nối cuối là tự xóa sạch bảng liền nên phải giữ riêng kết nối này mở suốt bài test
    private Connection keepAliveConnection;

    @BeforeEach
    void loadDatabase() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        db = new JdbcDatabase(new DatabaseConfig(url, "sa", ""));
        keepAliveConnection = db.open();
        loadScript("/schema.sql");
        loadScript("/fixtures.sql");
    }

    private void loadScript(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource);
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                var result = RunScript.execute(keepAliveConnection, reader)) {
        }
    }

    @AfterEach
    void closeDatabase() throws Exception {
        keepAliveConnection.close();
    }

    void execute(String sql, Object... parameters) throws Exception {
        try (var statement = keepAliveConnection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
