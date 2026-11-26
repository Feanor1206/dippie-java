package com.dippie.desktop.model;

// Số lượng tổng của 5 thẻ thống kê trên hàng đầu tab Tổng quan, tách cùng DashboardSnapshot sang model cho gọn tầng.
public record DashboardCounts(long series, long episodes, long users, long ratings, long progress) {
}
