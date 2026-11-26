package com.dippie.model.quiz;

import com.dippie.common.text.TextFolding;
import com.dippie.exception.InvalidRequestException;

import java.util.Optional;

// 6 khối lớp học từ mẫu giáo đến lớp 5 thay cho mức toán 1 đến 4 trước đây
public enum QuizGrade {

    MAU_GIAO(0, "Mẫu giáo", "Cộng trừ trong phạm vi 10, từ vựng và đố vui cho bé 5 tuổi"),
    LOP_1(1, "Lớp 1", "Cộng trừ trong phạm vi 20, kiến thức cơ bản lớp 1"),
    LOP_2(2, "Lớp 2", "Cộng trừ trong phạm vi 100 và làm quen phép nhân đơn giản"),
    LOP_3(3, "Lớp 3", "Bảng nhân chia từ 2 đến 9 và kiến thức lớp 3"),
    LOP_4(4, "Lớp 4", "Phép tính số lớn, nhân chia mở rộng và kiến thức lớp 4"),
    LOP_5(5, "Lớp 5", "Biểu thức nhiều bước, tỉ số phần trăm và kiến thức lớp 5");

    private final int order;
    private final String label;
    private final String description;

    QuizGrade(int order, String label, String description) {
        this.order = order;
        this.label = label;
        this.description = description;
    }

    public int order() {
        return order;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean hasNext() {
        return order < LOP_5.order;
    }

    public boolean hasPrevious() {
        return order > MAU_GIAO.order;
    }

    // lớp kế tiếp để dùng cho phim VIP hoặc gợi ý nâng lớp, chạm lớp 5 thì giữ nguyên lớp 5
    public QuizGrade nextGrade() {
        return switch (this) {
            case MAU_GIAO -> LOP_1;
            case LOP_1 -> LOP_2;
            case LOP_2 -> LOP_3;
            case LOP_3 -> LOP_4;
            case LOP_4, LOP_5 -> LOP_5;
        };
    }

    // lớp thấp hơn 1 bậc khi gợi ý giảm độ khó cho bé
    public QuizGrade previousGrade() {
        return switch (this) {
            case MAU_GIAO, LOP_1 -> MAU_GIAO;
            case LOP_2 -> LOP_1;
            case LOP_3 -> LOP_2;
            case LOP_4 -> LOP_3;
            case LOP_5 -> LOP_4;
        };
    }

    // ánh xạ mức toán cũ (1 đến 4) sang lớp học tương ứng khi nâng cấp dữ liệu
    public static QuizGrade fromLegacyMathLevel(int level) {
        return switch (level) {
            case 1 -> MAU_GIAO;
            case 2 -> LOP_1;
            case 3 -> LOP_2;
            case 4 -> LOP_3;
            default -> LOP_1;
        };
    }

    public static QuizGrade require(String raw) {
        return parseFlexible(raw)
                .orElseThrow(() -> new InvalidRequestException(
                        "Khối lớp chưa hợp lệ. Hãy chọn Mẫu giáo hoặc từ Lớp 1 đến Lớp 5."
                ));
    }

    // đọc linh hoạt cả mã hệ thống (LOP_3), tên tiếng Việt (Lớp 3) lẫn chữ không dấu hay số 0..5 cho file CSV
    public static Optional<QuizGrade> parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String folded = TextFolding.fold(raw).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        return switch (folded) {
            case "mau giao", "mau giao lon", "mg", "mam non", "0", "k" -> Optional.of(MAU_GIAO);
            case "lop 1", "lop1", "1" -> Optional.of(LOP_1);
            case "lop 2", "lop2", "2" -> Optional.of(LOP_2);
            case "lop 3", "lop3", "3" -> Optional.of(LOP_3);
            case "lop 4", "lop4", "4" -> Optional.of(LOP_4);
            case "lop 5", "lop5", "5" -> Optional.of(LOP_5);
            default -> Optional.empty();
        };
    }
}
