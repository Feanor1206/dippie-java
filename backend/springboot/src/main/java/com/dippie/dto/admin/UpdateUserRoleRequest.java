package com.dippie.dto.admin;

// body nhận role mới khi admin đổi quyền user
public record UpdateUserRoleRequest(String role) {
}
