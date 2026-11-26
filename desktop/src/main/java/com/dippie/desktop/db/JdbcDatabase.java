package com.dippie.desktop.db;

import java.sql.Connection;
import java.sql.SQLException;

// quản lý vòng đời kết nối và gom nhiều lệnh ghi vào chung 1 transaction JDBC
public final class JdbcDatabase {
    private final DatabaseConfig config;

    public JdbcDatabase(DatabaseConfig config) {
        this.config = config;
    }

    public Connection open() {
        return ConnectionProvider.INSTANCE.open(config);
    }

    // mở kết nối đọc rồi tự đóng bằng try-with-resources, dính lỗi SQL thì bọc sang DataAccessException.Query
    public <T> T read(Work<T> work) {
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException exception) {
            throw new DataAccessException.Query(exception);
        }
    }

    // chạy hết lệnh ghi trên cùng 1 kết nối, xong xuôi hết mới commit
    public <T> T transaction(Work<T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                // lỡ rollback cũng lỗi thì gắn vào suppressed kẻo mất lỗi gốc
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

    // functional interface cho 1 đoạn chạy JDBC nhận kết nối đang mở
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }
}
