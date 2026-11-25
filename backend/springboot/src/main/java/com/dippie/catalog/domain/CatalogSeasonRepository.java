package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogSeasonRepository extends JpaRepository<CatalogSeason, String> {

    List<CatalogSeason> findBySeriesIdOrderByNumberAsc(String seriesId);
}
