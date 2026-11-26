package com.dippie.controller.account;

import com.dippie.service.account.SessionLogoutService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Nhận lệnh POST /api/auth/logout để đăng xuất tài khoản khỏi mọi thiết bị và trả 204.
@RestController
@RequestMapping("/api/auth")
public class SessionLogoutController {

    private final SessionLogoutService sessionLogoutService;

    public SessionLogoutController(SessionLogoutService sessionLogoutService) {
        this.sessionLogoutService = sessionLogoutService;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        sessionLogoutService.logout();
        return ResponseEntity.noContent().build();
    }
}
