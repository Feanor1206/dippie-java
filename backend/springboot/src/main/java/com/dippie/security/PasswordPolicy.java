package com.dippie.security;

import com.dippie.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;

// Quy tắc kiểm tra mật khẩu: cấm bỏ trống hoặc toàn khoảng trắng, đủ độ dài tối thiểu và không vượt 72 byte UTF-8 vì BCrypt tự cắt phần thừa sau byte thứ 72.
public record PasswordPolicy(int minRegisterLength, int minAdminLength, int maxUtf8Bytes) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(6, 16, 72);
    public static final String BLANK_PASSWORD_MESSAGE = "Mật khẩu không được để trống hay chỉ có khoảng trắng.";

    // Đếm theo byte UTF-8 thay vì số ký tự vì chữ tiếng Việt có dấu tốn từ 2 đến 3 byte.
    public boolean exceedsMaxBytes(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes;
    }

    // Kiểm tra mật khẩu khi đăng ký hoặc user tự đổi mật khẩu.
    public void validateUserPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException(BLANK_PASSWORD_MESSAGE);
        }
        if (password.trim().length() < minRegisterLength) {
            throw new InvalidRequestException("Mật khẩu cần ít nhất " + minRegisterLength + " ký tự.");
        }
        if (exceedsMaxBytes(password)) {
            throw new InvalidRequestException("Mật khẩu không được dài quá " + maxUtf8Bytes + " byte UTF-8.");
        }
    }

    // Giữ chuẩn tối thiểu 16 ký tự mỗi khi admin đổi hoặc đặt lại mật khẩu, tránh tự hạ xuống mức yếu.
    public void validateAdminPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException(BLANK_PASSWORD_MESSAGE);
        }
        if (password.trim().length() < minAdminLength || exceedsMaxBytes(password)) {
            throw new InvalidRequestException(
                    "Mật khẩu quản trị cần từ " + minAdminLength + " ký tự đến tối đa " + maxUtf8Bytes + " byte.");
        }
    }

    // Kiểm tra mật khẩu admin lúc khởi tạo ứng dụng; sai thì chặn boot luôn bằng IllegalStateException.
    public void validateBootstrapPassword(String password) {
        if (password == null || password.isBlank() || password.trim().length() < minAdminLength || exceedsMaxBytes(password)) {
            throw new IllegalStateException(
                    "Mật khẩu quản trị cần từ " + minAdminLength + " ký tự đến tối đa " + maxUtf8Bytes + " byte.");
        }
    }
}
