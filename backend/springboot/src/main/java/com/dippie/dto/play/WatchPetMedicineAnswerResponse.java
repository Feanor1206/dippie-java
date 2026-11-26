package com.dippie.dto.play;

// kết quả kiểm tra bài toán hồi sức kèm trạng thái Dieppie mới nhất
public record WatchPetMedicineAnswerResponse(boolean correct, WatchPetStatusView watchPet) {
}
