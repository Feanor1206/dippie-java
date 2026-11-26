package com.dippie.security;

import com.dippie.model.user.Role;

// bản gọn của user đang đăng nhập, profileId null là đang ở hồ sơ chính còn có số là hồ sơ trẻ em
public record Viewer(Long userId, String username, Role role, Long profileId) {

    public Viewer(Long userId, String username, Role role) {
        this(userId, username, role, null);
    }

    public boolean isChildProfile() {
        return profileId != null;
    }

    // 0 là hồ sơ chính để query index gọn gàng khỏi dính NULL trong WHERE
    public long profileScopeId() {
        return profileId == null ? 0L : profileId;
    }

    // đang bật hồ sơ trẻ em thì không được tính là admin kể cả tài khoản gốc là ROLE_ADMIN
    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN && !isChildProfile();
    }
}
