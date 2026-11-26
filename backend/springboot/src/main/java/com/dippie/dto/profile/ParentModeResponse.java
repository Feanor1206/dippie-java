package com.dippie.dto.profile;

// trạng thái bật tắt chế độ hồ sơ trẻ em kèm số lượng hồ sơ hiện có
public record ParentModeResponse(boolean enabled, long profileCount) {
}
