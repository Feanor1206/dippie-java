package com.dippie.dto.play;

// kết quả kiểm tra bài toán hồi máu kèm trạng thái thú ảo mới nhất
public record WatchPetMedicineAnswerResponse(boolean correct, WatchPetStatusView watchPet) {
}
