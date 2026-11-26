package com.dippie.controller.account;

import com.dippie.dto.account.DeleteAccountRequest;
import com.dippie.service.account.AccountErasureService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Nhận lệnh DELETE /api/users/me để người dùng tự xóa tài khoản và trả 204 khi xong.
@RestController
@RequestMapping("/api/users/me")
public class AccountErasureController {

    private final AccountErasureService accountErasureService;

    public AccountErasureController(AccountErasureService accountErasureService) {
        this.accountErasureService = accountErasureService;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCurrentAccount(
            @RequestBody(required = false) DeleteAccountRequest request
    ) {
        accountErasureService.deleteCurrentAccount(request);
        return ResponseEntity.noContent().build();
    }
}
