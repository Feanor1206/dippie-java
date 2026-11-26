package com.dippie.repository.media;

import com.dippie.model.media.MediaAsset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// repo Spring Data JPA cho bảng media_assets
public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    List<MediaAsset> findByMovieIdAndAvailableTrueAndPublicAssetTrue(String movieId);
}
