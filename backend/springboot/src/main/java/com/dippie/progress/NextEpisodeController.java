package com.dippie.progress;

import com.dippie.common.web.ApiResponse;
import com.dippie.progress.NextEpisodeService.NextEpisodeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NextEpisodeController {

    private final NextEpisodeService nextEpisode;

    public NextEpisodeController(NextEpisodeService nextEpisode) {
        this.nextEpisode = nextEpisode;
    }

    @GetMapping("/api/users/next-episode")
    public ApiResponse<NextEpisodeView> find(@RequestParam String seriesId) {
        return nextEpisode.find(seriesId).map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok(null, "Bạn đã xem hết bộ phim này."));
    }
}
