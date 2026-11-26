package com.dippie.repository.play;

import com.dippie.model.play.ChildPlaySettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// repo cài đặt tính năng xem phim theo từng hồ sơ trẻ em
public interface ChildPlaySettingsRepository extends JpaRepository<ChildPlaySettings, Long> {

    List<ChildPlaySettings> findByProfileIdIn(Collection<Long> profileIds);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ChildPlaySettings s
            where s.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
