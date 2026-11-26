package com.dippie.repository.bounty;

import com.dippie.model.bounty.BountyTask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// repo bảng bounty_tasks, tra theo userId để cô lập hoàn toàn theo từng tài khoản phụ huynh
public interface BountyTaskRepository extends JpaRepository<BountyTask, Long> {

    List<BountyTask> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<BountyTask> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
