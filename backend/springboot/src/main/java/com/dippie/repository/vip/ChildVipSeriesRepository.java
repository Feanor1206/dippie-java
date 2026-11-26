package com.dippie.repository.vip;

import com.dippie.model.vip.ChildVipSeries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// repo đánh dấu phim bộ VIP theo từng hồ sơ bé
public interface ChildVipSeriesRepository extends JpaRepository<ChildVipSeries, Long> {

    boolean existsByProfileIdAndSeriesId(Long profileId, String seriesId);

    List<ChildVipSeries> findByProfileIdOrderByCreatedAtAscIdAsc(Long profileId);

    List<ChildVipSeries> findByProfileIdIn(Collection<Long> profileIds);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ChildVipSeries v
            where v.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
