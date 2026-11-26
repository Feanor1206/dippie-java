package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogGenre;

import org.springframework.data.jpa.repository.JpaRepository;

// Truy xuất CRUD cho bảng thể loại catalog_genres (mẫu Repository của Spring Data).
public interface CatalogGenreRepository extends JpaRepository<CatalogGenre, String> {
}
