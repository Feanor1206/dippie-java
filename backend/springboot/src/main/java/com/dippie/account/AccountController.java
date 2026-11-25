package com.dippie.account;

import com.dippie.common.web.ApiResponse;
import com.dippie.user.AuthResponse;
import com.dippie.user.UserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestBody(required = false) UpdateProfileRequest request
    ) {
        UserProfileResponse profile = accountService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.ok(profile, "Cập nhật thông tin tài khoản thành công"));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @RequestBody(required = false) ChangePasswordRequest request
    ) {
        AuthResponse response = accountService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đổi mật khẩu thành công"));
    }
}
