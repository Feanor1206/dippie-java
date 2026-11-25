package com.dippie.catalog.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CatalogSnapshotRepository extends JpaRepository<CatalogSnapshot, String> {

    List<CatalogSnapshot> findAllByOrderBySnapshotVersionDesc();

    Optional<CatalogSnapshot> findFirstByOrderBySnapshotVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findTopByOrderBySnapshotVersionDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogSnapshot> findFirstByContentHashOrderBySnapshotVersionDesc(String hash);
}
