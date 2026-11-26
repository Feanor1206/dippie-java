package com.dippie.controller.progress;

import com.dippie.service.progress.WatchHistoryClearService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint riêng cho nút xóa sạch toàn bộ lịch sử xem của người dùng hiện tại.
@RestController
@RequestMapping("/api/users/watch-progress")
public class WatchHistoryClearController {

    private final WatchHistoryClearService watchHistoryClearService;

    public WatchHistoryClearController(WatchHistoryClearService watchHistoryClearService) {
        this.watchHistoryClearService = watchHistoryClearService;
    }

    // Trả 204 kể cả khi lịch sử đã trống sẵn để bấm xóa lặp lại không bị lỗi.
    @DeleteMapping
    public ResponseEntity<Void> clearWatchProgress() {
        watchHistoryClearService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
