package com.dippie.dto.profile;

import java.util.List;

// payload tạo hoặc sửa hồ sơ trẻ em, kèm mã PIN phụ huynh, hạn mức ngày, khối lớp và các bộ câu hỏi đang bật
public record ChildProfileMutationRequest(
        String pin,
        String name,
        String avatarKey,
        Integer dailyMinutes,
        Boolean unlimited,
        String quizGrade,
        List<Long> quizSetIds
) {

    public ChildProfileMutationRequest(String pin, String name, String avatarKey) {
        this(pin, name, avatarKey, null, null, null, null);
    }
}
