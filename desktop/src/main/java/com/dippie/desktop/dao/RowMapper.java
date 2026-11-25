package com.dippie.desktop.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

// Functional interface chuyển một dòng ResultSet sang object Java cụ thể, dùng chung trong AbstractJdbcDao.
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
}
