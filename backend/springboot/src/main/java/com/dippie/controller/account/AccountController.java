package com.dippie.controller.account;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.account.ChangePasswordRequest;
import com.dippie.dto.account.UpdateProfileRequest;
import com.dippie.dto.user.AuthResponse;
import com.dippie.dto.user.UserProfileResponse;
import com.dippie.security.ClientIpResolver;
import com.dippie.service.account.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/me cho user đang đăng nhập tự sửa hồ sơ và đổi mật khẩu
@RestController
@RequestMapping("/api/users/me")
public class AccountController {

    private final AccountService accountService;
    private final ClientIpResolver clientIpResolver;

    public AccountController(AccountService accountService, ClientIpResolver clientIpResolver) {
        this.accountService = accountService;
        this.clientIpResolver = clientIpResolver;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestBody(required = false) UpdateProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        UserProfileResponse profile = accountService.updateProfile(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok(profile, "Cập nhật thông tin tài khoản thành công"));
    }

    // đổi mật khẩu xong trả luôn JWT mới vì tokenVersion tăng là mấy token cũ hết xài được
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @RequestBody(required = false) ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse response = accountService.changePassword(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok(response, "Đổi mật khẩu thành công"));
    }
}
