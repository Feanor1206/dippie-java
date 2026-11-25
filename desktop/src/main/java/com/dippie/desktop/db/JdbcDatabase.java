package com.dippie.desktop.db;

import java.sql.Connection;
import java.sql.SQLException;

// Quản lý vòng đời kết nối và gom nhiều lệnh ghi vào chung một giao dịch JDBC.
public final class JdbcDatabase {
    private final DatabaseConfig config;

    public JdbcDatabase(DatabaseConfig config) {
        this.config = config;
    }

    public Connection open() {
        return ConnectionProvider.INSTANCE.open(config);
    }

    // Mở một kết nối để đọc rồi tự đóng bằng try-with-resources, lỗi SQL thì bọc sang DataAccessException.Query.
    public <T> T read(Work<T> work) {
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException exception) {
            throw new DataAccessException.Query(exception);
        }
    }

    // Chạy mọi lệnh ghi trên cùng một kết nối và chỉ commit khi tất cả đều xong.
    public <T> T transaction(Work<T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                // Nếu rollback cũng lỗi thì gắn vào suppressed để không làm mất lỗi gốc.
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException exception) {
            throw new DataAccessException.Transaction(exception);
        }
    }

    // Functional interface cho một đoạn thao tác JDBC nhận kết nối đang mở.
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }
}
