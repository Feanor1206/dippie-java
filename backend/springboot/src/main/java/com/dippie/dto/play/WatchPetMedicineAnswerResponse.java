package com.dippie.dto.play;

// kết quả kiểm tra câu hỏi hồi sức kèm đáp án đúng, giải thích và trạng thái Dieppie mới nhất
public record WatchPetMedicineAnswerResponse(
        boolean correct,
        String correctOption,
        String correctText,
        String explanation,
        WatchPetStatusView watchPet
) {
}
