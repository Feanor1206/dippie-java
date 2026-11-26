package com.dippie.service.progress;

import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// xóa sạch lịch sử xem của tài khoản đang đăng nhập
@Service
public class WatchHistoryClearService {

    private final CurrentViewer currentViewer;
    private final WatchProgressRepository watchProgressRepository;

    public WatchHistoryClearService(CurrentViewer currentViewer, WatchProgressRepository watchProgressRepository) {
        this.currentViewer = currentViewer;
        this.watchProgressRepository = watchProgressRepository;
    }

    // bọc @Transactional để xóa gọn hết dòng watch_progress của đúng user đó trong 1 lượt
    @Transactional
    public void clearAll() {
        Viewer viewer = currentViewer.require();
        watchProgressRepository.deleteByUserId(viewer.userId());
    }
}
