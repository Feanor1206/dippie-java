package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// Lớp cha DAO trừu tượng gom sẵn cách chạy PreparedStatement, gắn tham số và ánh xạ bằng RowMapper.
// Các DAO con kế thừa lớp này rồi chỉ cần khai báo bảng, cột và mapper (kế thừa + generic).
public abstract class AbstractJdbcDao<T, ID> implements Dao<T, ID> {
    protected final JdbcDatabase db;
    private final String table;
    private final String select;
    private final String whereKey;
    private final String orderBy;
    private final RowMapper<T> mapper;
    private final Function<ID, Object[]> keyValues;

    protected AbstractJdbcDao(JdbcDatabase db, String table, String columns, RowMapper<T> mapper) {
        this(db, table, columns, mapper, List.of("id"), key -> new Object[]{key});
    }

    // Overload này nhận khóa ghép nhiều cột để dùng lại chung cho bảng nối như catalog_episode_assets.
    protected AbstractJdbcDao(JdbcDatabase db, String table, String columns, RowMapper<T> mapper,
                              List<String> keyColumns, Function<ID, Object[]> keyValues) {
        this.db = db;
        this.table = table;
        this.select = "SELECT " + columns + " FROM " + table;
        this.whereKey = " WHERE " + String.join(" = ? AND ", keyColumns) + " = ?";
        this.orderBy = " ORDER BY " + String.join(", ", keyColumns);
        this.mapper = mapper;
        this.keyValues = keyValues;
    }

    @Override
    public Optional<T> findById(ID id) {
        return db.read(connection -> findById(connection, id));
    }

    public Optional<T> findById(Connection connection, ID id) throws SQLException {
        return first(query(connection, select + whereKey, mapper, keyValues.apply(id)));
    }

    protected Optional<T> lockById(Connection connection, ID id) throws SQLException {
        return first(query(connection, select + whereKey + " FOR UPDATE", mapper, keyValues.apply(id)));
    }

    @Override
    public List<T> findAll() {
        return db.read(this::findAll);
    }

    public List<T> findAll(Connection connection) throws SQLException {
        return query(connection, select + orderBy, mapper);
    }

    @Override
    public long count() {
        return db.read(this::count);
    }

    public long count(Connection connection) throws SQLException {
        return query(connection, "SELECT COUNT(*) FROM " + table, row -> row.getLong(1)).getFirst();
    }

    // Luôn dùng PreparedStatement để tránh SQL injection và đóng gọn cả statement lẫn ResultSet.
    protected static <R> List<R> query(Connection connection, String sql, RowMapper<R> mapper,
                                       Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (var rows = statement.executeQuery()) {
                List<R> items = new ArrayList<>();
                while (rows.next()) {
                    items.add(mapper.map(rows));
                }
                return List.copyOf(items);
            }
        }
    }

    // Nhận Connection từ service truyền vào để câu lệnh ghi tham gia đúng giao dịch đang mở.
    protected static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    protected static <R> Optional<R> first(List<R> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
