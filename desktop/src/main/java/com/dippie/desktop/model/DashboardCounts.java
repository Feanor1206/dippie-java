package com.dippie.desktop.model;

// tổng số của 5 thẻ thống kê hàng đầu tab Tổng quan, tách cùng DashboardSnapshot qua model cho gọn tầng
public record DashboardCounts(long series, long episodes, long users, long ratings, long progress) {
}
