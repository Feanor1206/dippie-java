package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Đọc các phân đoạn của một tập phim theo thứ tự position.
public interface CatalogSegmentRepository extends JpaRepository<CatalogSegment, String> {

    List<CatalogSegment> findByEpisodeIdOrderByPositionAsc(String episodeId);
}
