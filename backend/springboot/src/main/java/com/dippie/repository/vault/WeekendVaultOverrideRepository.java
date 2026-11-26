package com.dippie.repository.vault;

import com.dippie.model.vault.WeekendVaultOverride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// repo bản ghi phụ huynh tự mở khóa két sắt phim cuối tuần cho từng hồ sơ bé theo tuần
public interface WeekendVaultOverrideRepository extends JpaRepository<WeekendVaultOverride, Long> {

    Optional<WeekendVaultOverride> findByProfileIdAndWeekStart(Long profileId, LocalDate weekStart);

    List<WeekendVaultOverride> findByProfileIdInAndWeekStart(Collection<Long> profileIds, LocalDate weekStart);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from WeekendVaultOverride o
            where o.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
