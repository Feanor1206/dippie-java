package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogSeason;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Truy vấn các mùa phim thuộc một phim bộ, sắp theo số mùa.
public interface CatalogSeasonRepository extends JpaRepository<CatalogSeason, String> {

    List<CatalogSeason> findBySeriesIdOrderByNumberAsc(String seriesId);
}
