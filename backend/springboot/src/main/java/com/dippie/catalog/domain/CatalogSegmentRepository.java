package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogSegmentRepository extends JpaRepository<CatalogSegment, String> {

    List<CatalogSegment> findByEpisodeIdOrderByPositionAsc(String episodeId);
}
