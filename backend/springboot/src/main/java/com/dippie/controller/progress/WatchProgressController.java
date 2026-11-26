package com.dippie.controller.progress;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.progress.WatchProgressView;
import com.dippie.service.progress.WatchProgressService;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// API đọc danh sách xem gần đây, lấy, lưu và xóa tiến độ xem từng tập của người đang đăng nhập.
@RestController
@RequestMapping("/api/users/watch-progress")
public class WatchProgressController {

    private final WatchProgressService watchProgressService;

    public WatchProgressController(WatchProgressService watchProgressService) {
        this.watchProgressService = watchProgressService;
    }

    @GetMapping
    public ApiResponse<List<WatchProgressView>> listRecent() {
        return ApiResponse.ok(watchProgressService.listRecent());
    }

    @GetMapping("/{episodeId}")
    public ApiResponse<WatchProgressView> getEpisodeProgress(@PathVariable String episodeId) {
        return ApiResponse.ok(watchProgressService.getForEpisode(episodeId));
    }

    @PutMapping("/{episodeId}")
    public ApiResponse<WatchProgressView> saveEpisodeProgress(
            @PathVariable String episodeId,
            @RequestBody JsonNode body
    ) {
        return ApiResponse.ok(watchProgressService.save(episodeId, body), "Đã lưu tiến độ xem.");
    }

    @DeleteMapping("/{episodeId}")
    public ApiResponse<Void> deleteEpisodeProgress(@PathVariable String episodeId) {
        watchProgressService.delete(episodeId);
        return ApiResponse.ok(null, "Đã xóa tiến độ xem.");
    }
}
