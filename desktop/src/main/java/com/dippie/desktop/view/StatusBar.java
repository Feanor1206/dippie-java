package com.dippie.desktop.view;

import com.dippie.desktop.controller.ConnectionState;
import com.dippie.desktop.controller.UiTasks;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

// Thanh trạng thái dưới cùng, nghe Observer từ UiTasks để cập nhật chấm kết nối, vòng quay bận và gợi ý phím tắt.
public final class StatusBar extends HBox {
    private final Circle dot = new Circle(4.5);
    private final Label label = new Label();
    private final Label shortcuts = new Label("Ctrl+F Tìm kiếm   •   Ctrl+Z Hoàn tác   •   F5 Làm mới");

    public StatusBar(UiTasks tasks) {
        super(12);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("status-bar");

        // Chấm màu và chữ báo trạng thái kết nối DB.
        label.getStyleClass().add("status-label");
        label.setTooltip(new Tooltip("Trạng thái kết nối cơ sở dữ liệu"));
        HBox connectionBox = new HBox(8, dot, label);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        // Vòng quay nhỏ chỉ hiện khi luồng nền đang chạy.
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(15, 15);
        spinner.setMaxSize(15, 15);
        Label busyLabel = new Label("Đang xử lý...");
        busyLabel.getStyleClass().add("muted-text");
        HBox busyBox = new HBox(6, spinner, busyLabel);
        busyBox.setAlignment(Pos.CENTER_LEFT);
        busyBox.visibleProperty().bind(tasks.busyProperty());
        busyBox.managedProperty().bind(busyBox.visibleProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        shortcuts.getStyleClass().add("status-hint");
        shortcuts.managedProperty().bind(shortcuts.visibleProperty());
        shortcuts.setVisible(false);

        getChildren().addAll(connectionBox, busyBox, spacer, shortcuts);
        display(tasks.connectionProperty().get());
        tasks.connectionProperty().addListener((property, before, after) -> display(after));
    }

    // Ẩn dòng phím tắt khi đang ở màn hình đăng nhập.
    public void setShortcutsVisible(boolean visible) {
        shortcuts.setVisible(visible);
    }

    // Đổi đồng thời cả chữ lẫn màu chấm tròn để nhìn là biết ngay trạng thái kết nối.
    private void display(ConnectionState state) {
        label.setText(state.label());
        dot.getStyleClass().setAll(state.style());
    }
}
