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

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<AdminUserView>>> listUsers(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.listUsers(query, page, size)));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserView>> changeRole(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserRoleRequest request
    ) {
        AdminUserView updated = adminUserService.changeRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật vai trò người dùng thành công"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserView>> changeStatus(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdateUserStatusRequest request
    ) {
        AdminUserView updated = adminUserService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật trạng thái người dùng thành công"));
    }
}
