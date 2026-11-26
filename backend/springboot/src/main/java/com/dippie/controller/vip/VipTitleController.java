package com.dippie.controller.vip;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.profile.MathChallengeAnswerRequest;
import com.dippie.dto.vip.ChildProfileVipSeriesView;
import com.dippie.dto.vip.KidVipStatusResponse;
import com.dippie.dto.vip.UpdateVipSeriesRequest;
import com.dippie.dto.vip.VipChallengeResponse;
import com.dippie.dto.vip.VipUnlockResponse;
import com.dippie.service.vip.VipTitleService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// API phim VIP theo từng hồ sơ bé: phụ huynh chọn phim bộ VIP và bé giải toán khó hơn 1 bậc để mở khóa từng tập trong ngày
@RestController
@RequestMapping("/api/users")
public class VipTitleController {

    private final VipTitleService vipTitleService;

    public VipTitleController(VipTitleService vipTitleService) {
        this.vipTitleService = vipTitleService;
    }

    @GetMapping("/vip-series")
    public ResponseEntity<ApiResponse<List<ChildProfileVipSeriesView>>> parentOverview() {
        return ResponseEntity.ok(ApiResponse.ok(vipTitleService.listParentOverview()));
    }

    @PutMapping("/profiles/{profileId}/vip-series")
    public ResponseEntity<ApiResponse<ChildProfileVipSeriesView>> updateChildVipSeries(
            @PathVariable Long profileId,
            @RequestBody(required = false) UpdateVipSeriesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                vipTitleService.updateChildVipSeries(profileId, request),
                "Đã lưu danh sách phim VIP cho bé."
        ));
    }

    @GetMapping("/vip-series/status")
    public ResponseEntity<ApiResponse<KidVipStatusResponse>> kidStatus() {
        return ResponseEntity.ok(ApiResponse.ok(vipTitleService.getKidStatus()));
    }

    @GetMapping("/vip-series/episodes/{episodeId}/challenge")
    public ResponseEntity<ApiResponse<VipChallengeResponse>> getChallenge(@PathVariable String episodeId) {
        return ResponseEntity.ok(ApiResponse.ok(vipTitleService.issueEpisodeChallenge(episodeId)));
    }

    @PostMapping("/vip-series/episodes/{episodeId}/unlock")
    public ResponseEntity<ApiResponse<VipUnlockResponse>> unlockEpisode(
            @PathVariable String episodeId,
            @RequestBody(required = false) MathChallengeAnswerRequest request
    ) {
        VipTitleService.UnlockOutcome outcome = vipTitleService.submitEpisodeUnlock(episodeId, request);
        return ResponseEntity.ok(ApiResponse.ok(outcome.response(), outcome.message()));
    }
}
