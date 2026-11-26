package com.dippie.controller.progress;

import com.dippie.service.progress.WatchHistoryClearService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint riêng cho nút xóa sạch lịch sử xem của user hiện tại
@RestController
@RequestMapping("/api/users/watch-progress")
public class WatchHistoryClearController {

    private final WatchHistoryClearService watchHistoryClearService;

    public WatchHistoryClearController(WatchHistoryClearService watchHistoryClearService) {
        this.watchHistoryClearService = watchHistoryClearService;
    }

    // lịch sử trống sẵn vẫn trả 204 để bấm xóa lặp lại khỏi báo lỗi
    @DeleteMapping
    public ResponseEntity<Void> clearWatchProgress() {
        watchHistoryClearService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
