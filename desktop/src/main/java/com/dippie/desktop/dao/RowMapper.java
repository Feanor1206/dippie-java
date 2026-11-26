package com.dippie.desktop.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

// functional interface chuyển 1 dòng ResultSet thành object Java, dùng chung trong AbstractJdbcDao
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
}
