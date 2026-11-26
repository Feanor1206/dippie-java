package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogGenre;

import org.springframework.data.jpa.repository.JpaRepository;

// CRUD bảng catalog_genres kiểu Repository của Spring Data
public interface CatalogGenreRepository extends JpaRepository<CatalogGenre, String> {
}
