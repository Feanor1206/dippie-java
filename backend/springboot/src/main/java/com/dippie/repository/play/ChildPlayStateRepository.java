package com.dippie.repository.play;

import com.dippie.model.play.ChildPlayState;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// repo trạng thái Dieppie và rương báu bay ngang của từng hồ sơ bé
public interface ChildPlayStateRepository extends JpaRepository<ChildPlayState, Long> {

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ChildPlayState s
            where s.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
