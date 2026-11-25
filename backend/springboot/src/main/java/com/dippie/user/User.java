package com.dippie.user;

import com.dippie.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
public class User {

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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }

    public void changePassword(String encoded) {
        this.password = encoded;
        this.tokenVersion++;
    }

    public void changePassword(String encodedPassword, LocalDateTime updatedAt) {
        changePassword(encodedPassword);
        this.updatedAt = updatedAt;
    }

    public void updateProfile(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public void updateProfile(String fullName, String email, LocalDateTime updatedAt) {
        updateProfile(fullName, email);
        this.updatedAt = updatedAt;
    }

    public void disable() {
        this.enabled = false;
        this.tokenVersion++;
    }

    public void disable(LocalDateTime updatedAt) {
        disable();
        this.updatedAt = updatedAt;
    }

    public void enable() {
        this.enabled = true;
        this.tokenVersion++;
    }

    public void enable(LocalDateTime updatedAt) {
        enable();
        this.updatedAt = updatedAt;
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
        this.tokenVersion++;
    }

    public void changeRole(Role newRole, LocalDateTime updatedAt) {
        changeRole(newRole);
        this.updatedAt = updatedAt;
    }
}
