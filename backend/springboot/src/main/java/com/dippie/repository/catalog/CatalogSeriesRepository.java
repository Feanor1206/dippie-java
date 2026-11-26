package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogSeries;

import org.springframework.data.jpa.repository.JpaRepository;

// Thao tác CRUD chuẩn trên bảng phim bộ catalog_series.
public interface CatalogSeriesRepository extends JpaRepository<CatalogSeries, String> {
}
