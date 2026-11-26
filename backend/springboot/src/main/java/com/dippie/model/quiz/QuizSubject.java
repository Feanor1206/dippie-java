package com.dippie.model.quiz;

import com.dippie.common.text.TextFolding;
import com.dippie.exception.InvalidRequestException;

import java.util.Optional;

// 6 nhóm môn học của ngân hàng câu hỏi trắc nghiệm
public enum QuizSubject {

    TOAN("Toán"),
    TIENG_VIET("Tiếng Việt"),
    TIENG_ANH("Tiếng Anh"),
    KHOA_HOC("Khoa học"),
    DO_VUI("Đố vui"),
    KHAC("Khác");

    private final String label;

    QuizSubject(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isEnglishSpeech() {
        return this == TIENG_ANH;
    }

    public static QuizSubject require(String raw) {
        return parseFlexible(raw)
                .orElseThrow(() -> new InvalidRequestException(
                        "Môn học chưa hợp lệ. Hãy chọn: Toán, Tiếng Việt, Tiếng Anh, Khoa học, Đố vui hoặc Khác."
                ));
    }

    // chấp nhận cả mã enum, tên tiếng Việt có dấu hoặc không dấu khi nhập từ CSV
    public static Optional<QuizSubject> parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String folded = TextFolding.fold(raw).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        return switch (folded) {
            case "toan", "toan hoc", "math" -> Optional.of(TOAN);
            case "tieng viet", "tv", "vietnamese" -> Optional.of(TIENG_VIET);
            case "tieng anh", "ta", "anh", "anh van", "english" -> Optional.of(TIENG_ANH);
            case "khoa hoc", "kh", "tu nhien", "tu nhien xa hoi", "science" -> Optional.of(KHOA_HOC);
            case "do vui", "vui", "riddle", "riddles" -> Optional.of(DO_VUI);
            case "khac", "other", "tong hop" -> Optional.of(KHAC);
            default -> Optional.empty();
        };
    }
}
