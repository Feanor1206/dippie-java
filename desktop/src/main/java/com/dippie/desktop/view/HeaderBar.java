package com.dippie.desktop.view;

import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.model.UserAccount;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;

// thanh đầu trang, bên trái hiện tên app còn bên phải hiện người đang đăng nhập với nút Đăng xuất
public final class HeaderBar extends HBox {
    private final Label avatar = new Label("Q");
    private final Label adminName = new Label();
    private final Label adminMeta = new Label();
    private final HBox sessionBox;

    public HeaderBar(UiTasks tasks, Runnable onLogout) {
        super(12);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("app-header");

        // cụm tên app ở góc trái
        Label mark = new Label("D");
        mark.getStyleClass().add("brand-mark");
        Label brand = new Label("Dippie Admin");
        brand.getStyleClass().add("brand");
        Label badge = new Label("Quản trị");
        badge.getStyleClass().add("brand-badge");
        HBox brandBox = new HBox(10, mark, brand, badge);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // khung tên admin với nút Đăng xuất bên phải, ẩn đi khi đang ở màn hình đăng nhập
        avatar.getStyleClass().add("admin-avatar");
        adminName.getStyleClass().add("admin-name");
        adminMeta.getStyleClass().add("admin-meta");
        VBox identity = new VBox(1, adminName, adminMeta);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox adminChip = new HBox(10, avatar, identity);
        adminChip.setAlignment(Pos.CENTER_LEFT);
        adminChip.getStyleClass().add("admin-chip");

        Button logout = new Button("Đăng xuất");
        logout.getStyleClass().add("secondary-button");
        logout.setTooltip(new Tooltip("Đăng xuất khỏi tài khoản hiện tại"));
        logout.disableProperty().bind(tasks.busyProperty());
        logout.setOnAction(event -> onLogout.run());

        sessionBox = new HBox(12, adminChip, logout);
        sessionBox.setAlignment(Pos.CENTER_LEFT);
        sessionBox.managedProperty().bind(sessionBox.visibleProperty());
        sessionBox.setVisible(false);

        getChildren().addAll(brandBox, spacer, sessionBox);
    }

    // hiện tạm chữ đang tải trong lúc lấy họ tên admin từ DB
    public void showLoadingSession() {
        avatar.setText("...");
        adminName.setText("Đang tải thông tin...");
        adminMeta.setText("Quản trị viên");
        sessionBox.setVisible(true);
    }

    // điền họ tên (trống thì lấy username) và chữ cái đầu làm avatar lên thanh trên cùng
    public void showSignedIn(UserAccount admin) {
        String displayName = admin.fullName() != null && !admin.fullName().isBlank()
                ? admin.fullName().strip() : admin.username();
        avatar.setText(displayName.substring(0, 1).toUpperCase(Locale.forLanguageTag("vi-VN")));
        adminName.setText(displayName);
        adminMeta.setText("@" + admin.username() + "  •  " + admin.role());
        sessionBox.setVisible(true);
    }

    // ẩn khung thông tin phiên khi quay về màn hình đăng nhập
    public void clearSignedIn() {
        sessionBox.setVisible(false);
    }
}
