package com.dippie.repository.play;

import com.dippie.model.play.ChildEpisodeRecapGate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// repo cổng khóa và mở khóa tập tiếp theo qua câu hỏi ôn tập cho hồ sơ trẻ em
public interface ChildEpisodeRecapGateRepository extends JpaRepository<ChildEpisodeRecapGate, Long> {

    Optional<ChildEpisodeRecapGate> findByProfileIdAndTargetEpisodeId(Long profileId, String targetEpisodeId);

    List<ChildEpisodeRecapGate> findByProfileIdAndTargetEpisodeIdInAndUnlockedFalse(
            Long profileId,
            Collection<String> targetEpisodeIds
    );

    List<ChildEpisodeRecapGate> findByProfileIdAndSeriesId(Long profileId, String seriesId);

    List<ChildEpisodeRecapGate> findByProfileIdAndUnlockedFalse(Long profileId);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ChildEpisodeRecapGate g
            where g.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
