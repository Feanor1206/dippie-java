package com.dippie.desktop.controller;

// Trạng thái kết nối DB để thanh trạng thái đổi chữ và màu chấm tròn.
// CONNECTED ghi đè onRequest() theo đa hình enum để thanh trạng thái khỏi nhấp nháy lại về đang kết nối mỗi lần truy vấn.
public enum ConnectionState {
    NOT_CHECKED("Chưa kiểm tra kết nối", "status-idle"),
    CHECKING("Đang kết nối...", "status-idle"),
    CONNECTED("Đã kết nối cơ sở dữ liệu", "status-connected") {
        @Override
        ConnectionState onRequest() {
            return this;
        }
    },
    DISCONNECTED("Mất kết nối cơ sở dữ liệu", "status-disconnected");

    private final String label;
    private final String style;

    ConnectionState(String label, String style) {
        this.label = label;
        this.style = style;
    }

    public String label() {
        return label;
    }

    public String style() {
        return style;
    }

    // Khi chưa rõ kết nối còn sống hay không thì lượt gọi DB mới sẽ chuyển sang trạng thái đang kết nối.
    ConnectionState onRequest() {
        return CHECKING;
    }
}
