package com.dippie.model.user;

import com.dippie.exception.InvalidRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.Objects;

// bảng users, gom hết mấy hàm đổi trạng thái vào trong User kiểu đóng gói
// chỗ nào đổi mật khẩu, đổi quyền hay khóa tài khoản cũng tự tăng tokenVersion đồng bộ
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_role_enabled", columnList = "role, enabled"))
public class User {

    private static final java.util.regex.Pattern USERNAME_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_.-]{3,50}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );
    private static final java.util.regex.Pattern TLD_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z]{2,}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("TRUE")
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    @ColumnDefault("0")
    private long tokenVersion;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected User() {
    }

    public User(
            String username,
            String email,
            String encodedPassword,
            String fullName,
            Role role,
            LocalDateTime createdAt
    ) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.fullName = fullName;
        this.role = role;
        this.enabled = true;
        this.tokenVersion = 0L;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void acceptTerms(LocalDateTime acceptedAt) {
        this.termsAcceptedAt = acceptedAt;
    }

    // tăng tokenVersion để cắt hết JWT đã cấp trước đó
    public void incrementTokenVersion(LocalDateTime updatedAt) {
        this.tokenVersion++;
        this.updatedAt = updatedAt;
    }

    // check username (3 đến 50 ký tự chữ, số, _, ., -), cấm @ kẻo đụng với tìm theo email lúc đăng nhập
    public static String validateUsername(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.length() < 3 || username.length() > 50) {
            throw new InvalidRequestException("Tên đăng nhập cần dài từ 3 đến 50 ký tự.");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new InvalidRequestException("Tên đăng nhập chỉ dùng chữ cái, số và các ký tự _, ., -.");
        }
        return username;
    }

    // hạ thường rồi check kỹ phần trước @ (không chấm đầu/cuối, không hai chấm liền) với phần TLD
    public static String validateEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.isEmpty() || email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidRequestException("Địa chỉ email chưa đúng định dạng.");
        }
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex + 1);
        int lastDot = domainPart.lastIndexOf('.');
        String tld = lastDot >= 0 ? domainPart.substring(lastDot + 1) : "";
        if (localPart.length() > 64
                || localPart.startsWith(".")
                || localPart.endsWith(".")
                || localPart.contains("..")
                || !TLD_PATTERN.matcher(tld).matches()) {
            throw new InvalidRequestException("Địa chỉ email chưa đúng định dạng.");
        }
        return email;
    }

    // user bỏ trống họ tên thì lấy tạm username hiện lên
    public static String resolveFullName(String rawFullName, String fallbackUsername) {
        if (rawFullName != null && rawFullName.length() > 100) {
            throw new InvalidRequestException("Họ tên không quá 100 ký tự.");
        }
        return (rawFullName == null || rawFullName.trim().isEmpty()) ? fallbackUsername : rawFullName.trim();
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    // đổi mật khẩu đã băm kèm tăng tokenVersion để đá hết phiên cũ
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.tokenVersion++;
    }

    public void changePassword(String encodedPassword, LocalDateTime updatedAt) {
        changePassword(encodedPassword);
        this.updatedAt = updatedAt;
    }

    // đổi họ tên hay email thì giữ nguyên tokenVersion kẻo user bị văng đăng nhập oan
    public void updateProfile(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public void updateProfile(String fullName, String email, LocalDateTime updatedAt) {
        updateProfile(fullName, email);
        this.updatedAt = updatedAt;
    }

    // khóa tài khoản kèm tăng tokenVersion để đá văng hết JWT còn hạn
    public void disable() {
        this.enabled = false;
        this.tokenVersion++;
    }

    public void disable(LocalDateTime updatedAt) {
        disable();
        this.updatedAt = updatedAt;
    }

    // mở khóa tài khoản rồi xoay tokenVersion bắt đăng nhập lại cho sạch
    public void enable() {
        this.enabled = true;
        this.tokenVersion++;
    }

    public void enable(LocalDateTime updatedAt) {
        enable();
        this.updatedAt = updatedAt;
    }

    // đổi role kèm tăng tokenVersion cho quyền mới ăn ngay, khỏi sót token cầm role cũ
    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
        this.tokenVersion++;
    }

    public void changeRole(Role newRole, LocalDateTime updatedAt) {
        changeRole(newRole);
        this.updatedAt = updatedAt;
    }
}
