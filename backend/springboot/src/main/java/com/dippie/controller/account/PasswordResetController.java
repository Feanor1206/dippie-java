package com.dippie.controller.account;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.account.PasswordResetConfirmRequest;
import com.dippie.dto.account.PasswordResetRequestDto;
import com.dippie.security.ClientIpResolver;
import com.dippie.service.account.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API công khai tiếp nhận yêu cầu quên mật khẩu và bước xác nhận đổi sang mật khẩu mới.
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    public PasswordResetController(PasswordResetService passwordResetService, ClientIpResolver clientIpResolver) {
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
    }

    // Luôn trả 202 với cùng một thông báo dù email có trong DB hay không để tránh bị dò tài khoản.
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> requestReset(
            @RequestBody(required = false) PasswordResetRequestDto request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.requestReset(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(null, PasswordResetService.REQUEST_ACCEPTED_MESSAGE));
    }

    // Kiểm tra token, đổi mật khẩu xong trả 204 không cần thân phản hồi.
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmReset(@RequestBody(required = false) PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        return ResponseEntity.noContent().build();
    }
}
