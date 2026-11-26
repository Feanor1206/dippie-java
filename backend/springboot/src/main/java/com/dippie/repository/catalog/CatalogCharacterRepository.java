package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogCharacter;

import org.springframework.data.jpa.repository.JpaRepository;

// repo nhân vật trong catalog dùng cho phần dựng câu đố ôn tập
public interface CatalogCharacterRepository extends JpaRepository<CatalogCharacter, String> {
}
