package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogSceneRepository extends JpaRepository<CatalogScene, String> {

    List<CatalogScene> findByEpisodeIdOrderByStartMsAsc(String episodeId);
}
