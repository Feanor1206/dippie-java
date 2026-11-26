package com.dippie.dto.profile;

// payload tạo hoặc sửa hồ sơ trẻ em, kèm mã PIN phụ huynh để xác nhận
public record ChildProfileMutationRequest(String pin, String name, String avatarKey) {
}
