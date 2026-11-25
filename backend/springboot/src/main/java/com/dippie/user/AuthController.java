package com.dippie.user;

import com.dippie.common.web.ApiResponse;
import com.dippie.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Các endpoint /api/auth cho đăng nhập, đăng ký và lấy hồ sơ tài khoản đang đăng nhập.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.login(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.ok(ApiResponse.ok(response, "Đăng nhập thành công"));
    }

    // Trả HTTP 201 kèm JWT ngay để client đăng nhập luôn sau khi tạo tài khoản.
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody(required = false) RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.register(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Đăng ký tài khoản thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentUserProfile()));
    }
}
