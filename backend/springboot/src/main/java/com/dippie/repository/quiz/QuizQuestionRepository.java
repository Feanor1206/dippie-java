package com.dippie.repository.quiz;

import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizQuestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// truy vấn và đếm câu hỏi theo bộ câu hỏi và khối lớp
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findBySetIdOrderByIdDesc(Long setId);

    List<QuizQuestion> findBySetIdInAndActiveTrueAndGrade(Collection<Long> setIds, QuizGrade grade);

    List<QuizQuestion> findBySetIdInAndActiveTrue(Collection<Long> setIds);

    Optional<QuizQuestion> findByIdAndSetId(Long id, Long setId);

    long countBySetId(Long setId);

    boolean existsBySeedKey(String seedKey);

    @Query("""
            select q.setId as setId, count(q) as questionCount
            from QuizQuestion q
            where q.setId in :setIds
            group by q.setId
            """)
    List<SetQuestionCount> countBySetIdIn(@Param("setIds") Collection<Long> setIds);

    void deleteBySetId(Long setId);

    @Modifying
    @Query("delete from QuizQuestion q where q.setId in (select s.id from QuizSet s where s.ownerUserId = :userId)")
    void deleteByOwnerUserId(@Param("userId") Long userId);

    interface SetQuestionCount {
        Long getSetId();

        long getQuestionCount();
    }
}
