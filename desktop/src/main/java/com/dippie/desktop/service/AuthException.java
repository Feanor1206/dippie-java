package com.dippie.desktop.service;

// Ngoại lệ đăng nhập tách rõ 3 lý do từ chối: sai thông tin, không có quyền quản trị và tài khoản bị khóa.
public final class AuthException extends RuntimeException {
    private final Reason reason;

    public AuthException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    // Gắn từng lý do từ chối với câu báo tiếng Việt hiện trên hộp thoại.
    public enum Reason {
        INVALID_CREDENTIALS("Sai tên đăng nhập, email hoặc mật khẩu."),
        NOT_ADMIN("Tài khoản này không phải quản trị viên."),
        LOCKED("Tài khoản này đã bị khóa.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
