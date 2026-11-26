package com.dippie.desktop.model;

// 2 vai trò khớp giá trị cột role trong bảng users, kèm nhãn tiếng Việt hiện lên giao diện
public enum Role {
    ROLE_USER("Người dùng"),
    ROLE_ADMIN("Quản trị viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
