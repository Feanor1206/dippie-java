package com.dippie.history;

import com.dippie.progress.WatchProgressRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service xóa toàn bộ bản ghi tiến độ xem thuộc về tài khoản đang đăng nhập.
@Service
public class WatchHistoryClearService {

    private final CurrentViewer currentViewer;
    private final WatchProgressRepository watchProgressRepository;

    public WatchHistoryClearService(CurrentViewer currentViewer, WatchProgressRepository watchProgressRepository) {
        this.currentViewer = currentViewer;
        this.watchProgressRepository = watchProgressRepository;
    }

    // Bọc trong @Transactional để xóa sạch các dòng watch_progress của đúng user hiện tại trong một lần ghi.
    @Transactional
    public void clearAll() {
        Viewer viewer = currentViewer.require();
        watchProgressRepository.deleteByUserId(viewer.userId());
    }
}
