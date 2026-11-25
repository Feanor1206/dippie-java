package com.dippie.catalog.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// Quản lý các bản ghi snapshot catalog theo phiên bản tăng dần và mã băm nội dung.
public interface CatalogSnapshotRepository extends JpaRepository<CatalogSnapshot, String> {

    List<CatalogSnapshot> findAllByOrderBySnapshotVersionDesc();

    Optional<CatalogSnapshot> findFirstByOrderBySnapshotVersionDesc();

    // Khóa ghi bi quan (PESSIMISTIC_WRITE) bản ghi mới nhất để hai luồng import chạy song song không cấp trùng snapshotVersion.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findTopByOrderBySnapshotVersionDesc();

    // Khóa ghi bi quan snapshot mới nhất trùng contentHash để chặn hai request import giống hệt nhau chạy đè lên nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findFirstByContentHashOrderBySnapshotVersionDesc(String hash);
}
