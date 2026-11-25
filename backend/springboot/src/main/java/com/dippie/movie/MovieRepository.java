package com.dippie.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MovieRepository extends JpaRepository<Movie, String>, JpaSpecificationExecutor<Movie> {
}
