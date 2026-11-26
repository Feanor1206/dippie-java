package com.dippie.repository.quiz;

import com.dippie.model.quiz.ChildQuizSet;
import com.dippie.model.quiz.ChildQuizSetId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// truy vấn danh sách bộ câu hỏi được gán cho từng hồ sơ bé
public interface ChildQuizSetRepository extends JpaRepository<ChildQuizSet, ChildQuizSetId> {

    List<ChildQuizSet> findByProfileIdOrderBySetIdAsc(Long profileId);

    List<ChildQuizSet> findByProfileIdIn(Collection<Long> profileIds);

    void deleteByProfileId(Long profileId);

    void deleteBySetId(Long setId);

    @Modifying
    @Query("delete from ChildQuizSet c where c.profileId in (select p.id from ChildProfile p where p.userId = :userId)")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from ChildQuizSet c where c.setId in (select s.id from QuizSet s where s.ownerUserId = :userId)")
    void deleteByOwnerUserId(@Param("userId") Long userId);
}
