package com.dippie.service.play;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

// gỡ ràng buộc enum cũ của h2 trên bảng screen_time_ledger để lưu được dòng loot không bị lỗi 500
@Component
@Order(1)
public class ScreenTimeLedgerSchemaReconciler implements ApplicationRunner {

    private final DataSource dataSource;

    public ScreenTimeLedgerSchemaReconciler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            reconcileH2Connection(connection);
        }
    }

    public static void reconcileH2Connection(Connection connection) throws SQLException {
        if (connection == null) {
            return;
        }
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null || !product.toUpperCase(Locale.ROOT).startsWith("H2")) {
            return;
        }
        if (!hasLedgerTable(connection)) {
            return;
        }

        List<String> staleConstraints = findStaleKindCheckConstraints(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE IF EXISTS screen_time_ledger DROP CONSTRAINT IF EXISTS ck_screen_time_ledger_kind");
            for (String constraintName : staleConstraints) {
                if (constraintName.matches("[A-Za-z0-9_]+")) {
                    statement.execute("ALTER TABLE screen_time_ledger DROP CONSTRAINT IF EXISTS " + constraintName);
                }
            }
            statement.execute("ALTER TABLE screen_time_ledger ALTER COLUMN kind VARCHAR(20) NOT NULL");
        }
    }

    private static boolean hasLedgerTable(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'SCREEN_TIME_LEDGER'";
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static List<String> findStaleKindCheckConstraints(Connection connection) throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE UPPER(tc.TABLE_NAME) = 'SCREEN_TIME_LEDGER'
                  AND UPPER(tc.CONSTRAINT_TYPE) = 'CHECK'
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String constraintName = rs.getString(1);
                String checkClause = rs.getString(2);
                if (constraintName == null || checkClause == null) {
                    continue;
                }
                String upperClause = checkClause.toUpperCase(Locale.ROOT);
                if (upperClause.contains("KIND") && !upperClause.contains("LOOT")) {
                    names.add(constraintName);
                }
            }
        }
        return names;
    }
}
