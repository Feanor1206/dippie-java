package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogCharacterSeriesRepository extends JpaRepository<CatalogCharacterSeries, CatalogCharacterSeries.Key> {
}
