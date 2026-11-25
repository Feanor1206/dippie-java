package com.dippie.admin;

import com.dippie.common.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API quản trị tài khoản người dùng: tìm kiếm phân trang, đổi quyền và bật tắt tài khoản.
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // Lọc tài khoản theo tên đăng nhập hoặc email; để trống query thì trả toàn bộ theo trang.
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<AdminUserView>>> listUsers(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.listUsers(query, page, size)));
    }

    // Đổi vai trò cho tài khoản có id tương ứng.
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserView>> changeRole(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserRoleRequest request
    ) {
        AdminUserView updated = adminUserService.changeRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật vai trò người dùng."));
    }

    // Bật hoặc khóa tài khoản người dùng theo id.
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserView>> changeStatus(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserStatusRequest request
    ) {
        AdminUserView updated = adminUserService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật trạng thái tài khoản."));
    }
}
