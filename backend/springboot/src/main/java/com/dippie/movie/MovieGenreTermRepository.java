package com.dippie.movie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieGenreTermRepository extends JpaRepository<MovieGenreTerm, String> {
}
