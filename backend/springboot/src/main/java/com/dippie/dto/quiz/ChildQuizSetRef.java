package com.dippie.dto.quiz;

// thông tin rút gọn của 1 bộ câu hỏi đang gán cho bé để hiển thị trên thẻ hồ sơ
public record ChildQuizSetRef(Long id, String name, boolean builtIn, boolean dynamicGenerator, boolean active) {
}
