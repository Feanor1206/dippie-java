package com.dippie.controller.admin;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.admin.AdminPageResponse;
import com.dippie.dto.admin.AdminUserView;
import com.dippie.dto.admin.UpdateUserRoleRequest;
import com.dippie.dto.admin.UpdateUserStatusRequest;
import com.dippie.service.admin.AdminUserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API quản trị user, gồm tìm phân trang, đổi quyền và khóa/mở khóa
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // lọc theo username hoặc email, bỏ trống query thì trả hết theo trang
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<AdminUserView>>> listUsers(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.listUsers(query, page, size)));
    }

    // đổi vai trò của user theo id
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserView>> changeRole(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserRoleRequest request
    ) {
        AdminUserView updated = adminUserService.changeRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật vai trò người dùng."));
    }

    // khóa hoặc mở khóa user theo id
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserView>> changeStatus(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserStatusRequest request
    ) {
        AdminUserView updated = adminUserService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật trạng thái tài khoản."));
    }
}
