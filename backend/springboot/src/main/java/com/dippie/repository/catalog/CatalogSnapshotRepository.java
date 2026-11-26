package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogSnapshot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// repo quản lý snapshot catalog theo version với mã băm nội dung
public interface CatalogSnapshotRepository extends JpaRepository<CatalogSnapshot, String> {

    List<CatalogSnapshot> findAllByOrderBySnapshotVersionDesc();

    Optional<CatalogSnapshot> findFirstByOrderBySnapshotVersionDesc();

    // khóa PESSIMISTIC_WRITE bản mới nhất kẻo hai luồng import song song cấp trùng snapshotVersion
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findTopByOrderBySnapshotVersionDesc();

    // khóa PESSIMISTIC_WRITE snapshot mới nhất trùng contentHash chặn hai request import giống hệt chạy đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findFirstByContentHashOrderBySnapshotVersionDesc(String hash);
}
