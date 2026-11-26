package com.dippie.service.quiz;

import com.dippie.model.quiz.BuiltInQuizCatalog;
import com.dippie.model.quiz.QuizQuestion;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;
import com.dippie.repository.quiz.QuizQuestionRepository;
import com.dippie.repository.quiz.QuizSetRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// nạp sẵn 3 bộ câu hỏi mặc định của hệ thống (Toán tự động, 30 câu Tiếng Anh, 30 câu Đố vui & Khoa học) theo kiểu chạy lặp lại không trùng
@Component
@Order(5)
public class BuiltInQuizSeeder implements ApplicationRunner {

    private final QuizSetRepository quizSetRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final TransactionTemplate writes;
    private final Clock clock;

    public BuiltInQuizSeeder(
            QuizSetRepository quizSetRepository,
            QuizQuestionRepository quizQuestionRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.quizSetRepository = quizSetRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.writes = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSeeded();
    }

    // đảm bảo 3 bộ có sẵn cùng 60 câu mặc định luôn tồn tại (an toàn kể cả khi test vừa dọn bảng)
    public synchronized void ensureSeeded() {
        writes.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

            ensureSet(
                    QuizSet.SYSTEM_KEY_MATH,
                    "Toán học",
                    "Tự động tạo câu hỏi toán trắc nghiệm phù hợp từng lớp, có đáp án nhiễu gần đúng",
                    QuizSubject.TOAN,
                    true,
                    now
            );

            QuizSet englishSet = ensureSet(
                    QuizSet.SYSTEM_KEY_ENGLISH,
                    "Tiếng Anh từ vựng",
                    "30 câu từ vựng tiếng Anh gần gũi theo từng lớp, bấm Nghe đọc chuẩn giọng Anh-Mỹ",
                    QuizSubject.TIENG_ANH,
                    false,
                    now
            );
            seedQuestions(englishSet.getId(), BuiltInQuizCatalog.englishQuestions(), now);

            QuizSet scienceSet = ensureSet(
                    QuizSet.SYSTEM_KEY_SCIENCE_RIDDLES,
                    "Đố vui & Khoa học",
                    "30 câu đố vui và khám phá khoa học tự nhiên thú vị bằng tiếng Việt cho bé",
                    QuizSubject.KHOA_HOC,
                    false,
                    now
            );
            seedQuestions(scienceSet.getId(), BuiltInQuizCatalog.scienceAndRiddleQuestions(), now);
        });
    }

    private QuizSet ensureSet(
            String systemKey,
            String name,
            String description,
            QuizSubject defaultSubject,
            boolean dynamicGenerator,
            LocalDateTime now
    ) {
        return quizSetRepository.findBySystemKey(systemKey)
                .orElseGet(() -> quizSetRepository.saveAndFlush(
                        QuizSet.builtIn(systemKey, name, description, defaultSubject, dynamicGenerator, now)
                ));
    }

    private void seedQuestions(Long setId, List<BuiltInQuizCatalog.SeedQuestion> items, LocalDateTime now) {
        for (BuiltInQuizCatalog.SeedQuestion item : items) {
            QuizQuestion existing = quizQuestionRepository.findBySeedKey(item.seedKey()).orElse(null);
            if (existing == null) {
                quizQuestionRepository.save(new QuizQuestion(
                        setId,
                        item.seedKey(),
                        item.questionText(),
                        item.optionA(),
                        item.optionB(),
                        item.optionC(),
                        item.optionD(),
                        item.correctOption(),
                        item.explanation(),
                        item.subject(),
                        item.grade(),
                        true,
                        now
                ));
            } else if (!existing.getQuestionText().equals(item.questionText())) {
                // đồng bộ lại câu hỏi mặc định khi cập nhật lời dẫn song ngữ cho bé nhỏ
                existing.update(
                        item.questionText(),
                        item.optionA(),
                        item.optionB(),
                        item.optionC(),
                        item.optionD(),
                        item.correctOption(),
                        item.explanation(),
                        item.subject(),
                        item.grade(),
                        existing.isActive(),
                        now
                );
                quizQuestionRepository.save(existing);
            }
        }
        quizQuestionRepository.flush();
    }
}
