package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogSegment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// lấy các phân đoạn của tập theo thứ tự position
public interface CatalogSegmentRepository extends JpaRepository<CatalogSegment, String> {

    List<CatalogSegment> findByEpisodeIdOrderByPositionAsc(String episodeId);
}
