package com.dippie.dto.profile;

// payload tạo hoặc sửa hồ sơ trẻ em, kèm mã PIN phụ huynh và tùy chọn hạn mức xem mỗi ngày
public record ChildProfileMutationRequest(
        String pin,
        String name,
        String avatarKey,
        Integer dailyMinutes,
        Boolean unlimited
) {

    public ChildProfileMutationRequest(String pin, String name, String avatarKey) {
        this(pin, name, avatarKey, null, null);
    }
}
