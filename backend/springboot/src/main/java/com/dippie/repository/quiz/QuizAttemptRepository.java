package com.dippie.repository.quiz;

import com.dippie.model.quiz.QuizAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// đọc 30 câu gần nhất để né hỏi trùng và thống kê câu trả lời theo tuần của từng bé
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findTop30ByProfileIdOrderByAnsweredAtDescIdDesc(Long profileId);

    List<QuizAttempt> findByProfileIdAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDescIdDesc(
            Long profileId,
            LocalDateTime sinceUtc
    );

    List<QuizAttempt> findByProfileIdInAndAnsweredAtGreaterThanEqualOrderByAnsweredAtDescIdDesc(
            Collection<Long> profileIds,
            LocalDateTime sinceUtc
    );

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("delete from QuizAttempt a where a.profileId in (select p.id from ChildProfile p where p.userId = :userId)")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update QuizAttempt a set a.setId = null, a.questionId = null where a.setId = :setId")
    void clearReferencesBySetId(@Param("setId") Long setId);

    @Modifying
    @Query("update QuizAttempt a set a.questionId = null where a.questionId = :questionId")
    void clearQuestionReference(@Param("questionId") Long questionId);

    @Modifying
    @Query("update QuizAttempt a set a.setId = null, a.questionId = null where a.setId in (select s.id from QuizSet s where s.ownerUserId = :userId)")
    void clearReferencesByOwnerUserId(@Param("userId") Long userId);
}
