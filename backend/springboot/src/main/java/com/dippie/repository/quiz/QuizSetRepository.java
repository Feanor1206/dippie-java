package com.dippie.repository.quiz;

import com.dippie.model.quiz.QuizSet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// thao tác bảng quiz_sets cho bộ câu hỏi hệ thống và bộ riêng từng phụ huynh
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {

    Optional<QuizSet> findBySystemKey(String systemKey);

    List<QuizSet> findByBuiltInTrueOrderByIdAsc();

    List<QuizSet> findByBuiltInTrueAndActiveTrueOrderByIdAsc();

    List<QuizSet> findByOwnerUserIdOrderByCreatedAtDescIdDesc(Long ownerUserId);

    long countByOwnerUserId(Long ownerUserId);

    Optional<QuizSet> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSet s where s.id = :id and s.ownerUserId = :ownerUserId")
    Optional<QuizSet> findByIdAndOwnerUserIdForUpdate(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId
    );

    List<QuizSet> findByIdIn(Collection<Long> ids);

    void deleteByOwnerUserId(Long ownerUserId);
}
