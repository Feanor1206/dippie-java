package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogSeries;

import org.springframework.data.jpa.repository.JpaRepository;

// CRUD chuẩn cho bảng catalog_series
public interface CatalogSeriesRepository extends JpaRepository<CatalogSeries, String> {
}
