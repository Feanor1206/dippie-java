package com.dippie.desktop.dao;

import java.util.List;
import java.util.Optional;

// interface chung cho mọi DAO chỉ đọc theo thực thể T và kiểu khóa chính ID
public interface Dao<T, ID> {
    Optional<T> findById(ID id);

    List<T> findAll();

    long count();
}
