package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogEpisodeRepository extends JpaRepository<CatalogEpisode, String> {

    List<CatalogEpisode> findBySeriesIdOrderByNumberAsc(String seriesId);

    List<CatalogEpisode> findBySeriesIdAndPublicationStatusOrderByNumberAsc(String seriesId, String publicationStatus);
}
