package com.dippie.repository.bounty;

import com.dippie.model.bounty.BountySubmission;
import com.dippie.model.bounty.BountySubmissionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

// repo lượt nộp nhiệm vụ của bé, kiểm tra trùng PENDING, lọc theo ngày và dọn khi xóa hồ sơ
public interface BountySubmissionRepository extends JpaRepository<BountySubmission, Long> {

    boolean existsByTaskIdAndProfileIdAndStatus(Long taskId, Long profileId, BountySubmissionStatus status);

    boolean existsByTaskIdAndProfileIdAndStatusAndUsageDay(
            Long taskId,
            Long profileId,
            BountySubmissionStatus status,
            LocalDate usageDay
    );

    List<BountySubmission> findByProfileIdOrderBySubmittedAtDescIdDesc(Long profileId);

    List<BountySubmission> findByProfileIdInOrderBySubmittedAtDescIdDesc(Collection<Long> profileIds);

    void deleteByTaskId(Long taskId);

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from BountySubmission s
            where s.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
