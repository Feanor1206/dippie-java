package com.dippie.controller.progress;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.progress.NextEpisodeView;
import com.dippie.service.progress.NextEpisodeService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API gợi ý tập tiếp theo nên phát trong một bộ phim cho tài khoản đang đăng nhập.
@RestController
public class NextEpisodeController {

    private final NextEpisodeService nextEpisode;

    public NextEpisodeController(NextEpisodeService nextEpisode) {
        this.nextEpisode = nextEpisode;
    }

    // Nếu người dùng đã xem hết mọi tập hợp lệ thì vẫn trả 200 với data là null kèm thông báo thay vì báo 404.
    @GetMapping("/api/users/next-episode")
    public ApiResponse<NextEpisodeView> find(@RequestParam String seriesId) {
        return nextEpisode.find(seriesId).map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok(null, "Bạn đã xem hết bộ phim này."));
    }
}
