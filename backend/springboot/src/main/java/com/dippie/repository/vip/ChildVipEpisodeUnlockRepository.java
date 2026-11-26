package com.dippie.repository.vip;

import com.dippie.model.vip.ChildVipEpisodeUnlock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// repo lưu từng tập VIP mà bé đã mở khóa trong ngày theo giờ Việt Nam
public interface ChildVipEpisodeUnlockRepository extends JpaRepository<ChildVipEpisodeUnlock, Long> {

    boolean existsByProfileIdAndEpisodeIdAndUnlockDay(Long profileId, String episodeId, LocalDate unlockDay);

    List<ChildVipEpisodeUnlock> findByProfileIdAndUnlockDay(Long profileId, LocalDate unlockDay);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ChildVipEpisodeUnlock u
            where u.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
