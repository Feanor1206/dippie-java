package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogPackage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// đọc lịch sử gói catalog đã import, chủ yếu kéo bản importedAt mới nhất trước
public interface CatalogPackageRepository extends JpaRepository<CatalogPackage, String> {

    List<CatalogPackage> findAllByOrderByImportedAtDesc();

    Optional<CatalogPackage> findFirstByOrderByImportedAtDesc();
}
