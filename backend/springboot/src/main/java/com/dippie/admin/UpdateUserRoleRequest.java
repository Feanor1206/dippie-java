package com.dippie.admin;

// Payload nhận vai trò mới khi admin đổi quyền một tài khoản.
public record UpdateUserRoleRequest(String role) {
}
