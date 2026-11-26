package com.dippie.controller.play;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.play.ChildProfilePlaySettingsView;
import com.dippie.dto.play.UpdatePlaySettingsRequest;
import com.dippie.service.play.PlaySettingsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// phụ huynh xem và chỉnh cài đặt thú ảo, rương báu, ôn tập cho từng hồ sơ bé (cần PIN phụ huynh)
@RestController
@RequestMapping("/api/users/profiles")
public class ChildPlaySettingsController {

    private final PlaySettingsService playSettingsService;

    public ChildPlaySettingsController(PlaySettingsService playSettingsService) {
        this.playSettingsService = playSettingsService;
    }

    @GetMapping("/play-features")
    public ResponseEntity<ApiResponse<List<ChildProfilePlaySettingsView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(playSettingsService.listParentSettings()));
    }

    @PutMapping("/{profileId}/play-features")
    public ResponseEntity<ApiResponse<ChildProfilePlaySettingsView>> update(
            @PathVariable Long profileId,
            @RequestBody(required = false) UpdatePlaySettingsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                playSettingsService.updateParentSettings(profileId, request),
                "Đã lưu cài đặt tính năng xem phim cho bé."
        ));
    }
}
