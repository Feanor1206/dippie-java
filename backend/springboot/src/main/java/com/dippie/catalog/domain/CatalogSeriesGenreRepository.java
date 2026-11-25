package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSeriesGenreRepository extends JpaRepository<CatalogSeriesGenre, CatalogSeriesGenre.Key> {
}
