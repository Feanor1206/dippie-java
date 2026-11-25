package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEpisodeAssetRepository extends JpaRepository<CatalogEpisodeAsset, CatalogEpisodeAsset.Key> {
}
