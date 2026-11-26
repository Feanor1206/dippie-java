package com.dippie.repository.profile;

import com.dippie.model.profile.ChildProfile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// repo bảng child_profiles, tra theo userId để không bao giờ lộ hồ sơ nhà khác
public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {

    List<ChildProfile> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<ChildProfile> findByIdAndUserId(Long id, Long userId);

    // khóa ghi đúng dòng hồ sơ bé khi trừ heartbeat hay cộng phút thưởng để tránh đụng song song
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChildProfile c where c.id = :id and c.userId = :userId")
    Optional<ChildProfile> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
