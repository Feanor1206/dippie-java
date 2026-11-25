package com.dippie.movie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenre.Key> {
}
