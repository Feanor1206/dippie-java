package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogScene;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// kéo danh sách phân cảnh của tập theo startMs tăng dần
public interface CatalogSceneRepository extends JpaRepository<CatalogScene, String> {

    List<CatalogScene> findByEpisodeIdOrderByStartMsAsc(String episodeId);
}
