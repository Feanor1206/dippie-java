package com.dippie.controller.profile;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.ChangeParentPinRequest;
import com.dippie.dto.profile.ParentModeResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.service.profile.ParentModeService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/users/parent-mode cho phụ huynh bật tắt chế độ hồ sơ trẻ em và đổi mã PIN
@RestController
@RequestMapping("/api/users/parent-mode")
public class ParentModeController {

    private final ParentModeService parentModeService;

    public ParentModeController(ParentModeService parentModeService) {
        this.parentModeService = parentModeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ParentModeResponse>> status() {
        return ResponseEntity.ok(ApiResponse.ok(parentModeService.status()));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<ParentModeResponse>> enable(
            @RequestBody(required = false) PinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(parentModeService.enable(request), "Đã bật chế độ hồ sơ trẻ em."));
    }

    @PutMapping("/pin")
    public ResponseEntity<ApiResponse<ParentModeResponse>> changePin(
            @RequestBody(required = false) ChangeParentPinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(parentModeService.changePin(request), "Đã đổi mã PIN phụ huynh."));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<ParentModeResponse>> disable(
            @RequestBody(required = false) PinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(parentModeService.disable(request), "Đã tắt chế độ hồ sơ trẻ em."));
    }
}
