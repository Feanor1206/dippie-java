package com.dippie.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Repo JPA cho bảng movies, mở rộng JpaSpecificationExecutor để nhận bộ lọc động từ MovieFilter.
public interface MovieRepository extends JpaRepository<Movie, String>, JpaSpecificationExecutor<Movie> {
}
