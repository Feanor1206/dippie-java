package com.dippie.repository.movie;

import com.dippie.model.movie.Movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// repo JPA bảng movies, thêm JpaSpecificationExecutor để nhận bộ lọc động từ MovieFilter
public interface MovieRepository extends JpaRepository<Movie, String>, JpaSpecificationExecutor<Movie> {
}
