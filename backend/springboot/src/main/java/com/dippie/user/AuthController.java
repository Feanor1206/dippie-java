package com.dippie.user;

import com.dippie.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.login(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.ok(response, "Đăng nhập thành công"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody(required = false) RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.register(request, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Đăng ký tài khoản thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentUserProfile()));
    }
}
