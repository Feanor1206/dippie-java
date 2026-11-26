package com.dippie.repository.bounty;

import com.dippie.model.bounty.BountyTaskProfile;
import com.dippie.model.bounty.BountyTaskProfileId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// repo bảng nối bounty_task_profiles giữa nhiệm vụ và các hồ sơ bé được nhìn thấy
public interface BountyTaskProfileRepository extends JpaRepository<BountyTaskProfile, BountyTaskProfileId> {

    List<BountyTaskProfile> findByTaskIdIn(Collection<Long> taskIds);

    List<BountyTaskProfile> findByProfileId(Long profileId);

    boolean existsByTaskIdAndProfileId(Long taskId, Long profileId);

    void deleteByTaskId(Long taskId);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from BountyTaskProfile link
            where link.taskId in (select t.id from BountyTask t where t.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
