package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

// Thao tác CRUD chuẩn trên bảng phim bộ catalog_series.
public interface CatalogSeriesRepository extends JpaRepository<CatalogSeries, String> {
}
