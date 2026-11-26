package com.dippie.controller.vault;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.vault.ChildProfileWeekendVaultView;
import com.dippie.dto.vault.WeekendVaultStatusView;
import com.dippie.service.vault.WeekendVaultService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// API két sắt phim cuối tuần cho bé xem thanh Vé xem phim và phụ huynh mở khóa bằng PIN
@RestController
@RequestMapping("/api/users")
public class WeekendVaultController {

    private final WeekendVaultService weekendVaultService;

    public WeekendVaultController(WeekendVaultService weekendVaultService) {
        this.weekendVaultService = weekendVaultService;
    }

    @GetMapping("/weekend-vault")
    public ResponseEntity<ApiResponse<WeekendVaultStatusView>> currentStatus() {
        return ResponseEntity.ok(ApiResponse.ok(weekendVaultService.getCurrentStatus()));
    }

    @GetMapping("/profiles/weekend-vault")
    public ResponseEntity<ApiResponse<List<ChildProfileWeekendVaultView>>> parentOverview() {
        return ResponseEntity.ok(ApiResponse.ok(weekendVaultService.listParentOverview()));
    }

    @PostMapping("/profiles/{profileId}/weekend-vault/unlock")
    public ResponseEntity<ApiResponse<ChildProfileWeekendVaultView>> unlockManually(
            @PathVariable Long profileId,
            @RequestBody(required = false) PinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                weekendVaultService.unlockManually(profileId, request),
                "Đã mở khóa két sắt phim cuối tuần cho bé."
        ));
    }

    @PostMapping("/profiles/{profileId}/weekend-vault/lock")
    public ResponseEntity<ApiResponse<ChildProfileWeekendVaultView>> restoreLock(
            @PathVariable Long profileId,
            @RequestBody(required = false) PinRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                weekendVaultService.restoreLock(profileId, request),
                "Đã bật lại khóa két sắt phim cuối tuần."
        ));
    }
}
