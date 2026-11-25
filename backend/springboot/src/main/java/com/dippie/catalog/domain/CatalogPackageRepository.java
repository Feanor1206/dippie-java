package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Đọc lịch sử các gói catalog đã import, chủ yếu lấy theo importedAt mới nhất trước.
public interface CatalogPackageRepository extends JpaRepository<CatalogPackage, String> {

    List<CatalogPackage> findAllByOrderByImportedAtDesc();

    Optional<CatalogPackage> findFirstByOrderByImportedAtDesc();
}
