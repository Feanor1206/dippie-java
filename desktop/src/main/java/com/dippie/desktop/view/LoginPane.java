package com.dippie.desktop.view;

import com.dippie.desktop.controller.LoginController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.model.AdminSession;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

// Màn hình đăng nhập (MVC), nhấn Enter hay bấm nút đều chạy qua cùng một lambda gọi về LoginController.
public final class LoginPane extends StackPane {
    public LoginPane(LoginController controller, UiTasks tasks, Consumer<AdminSession> onSuccess) {
        getStyleClass().add("login-canvas");
        setAlignment(Pos.CENTER);

        // Phần tiêu đề phía trên thẻ đăng nhập.
        Label badge = new Label("Quản trị Dippie");
        badge.getStyleClass().add("brand-badge");
        Label title = new Label("Đăng nhập");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Dùng tài khoản quản trị viên để vào xem danh mục phim và quản lý thành viên.");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);
        VBox heading = new VBox(6, badge, title, subtitle);

        // Hai ô nhập tên đăng nhập hoặc email và mật khẩu.
        TextField identifier = new TextField();
        identifier.setPromptText("Ví dụ: admin hoặc admin@dippie.com");
        identifier.setTooltip(new Tooltip("Tên đăng nhập hoặc email quản trị viên"));
        PasswordField password = new PasswordField();
        password.setPromptText("Nhập mật khẩu");
        password.setTooltip(new Tooltip("Mật khẩu đăng nhập"));

        Label identifierLabel = new Label("Tên đăng nhập hoặc email");
        identifierLabel.getStyleClass().add("field-label");
        identifierLabel.setLabelFor(identifier);
        Label passwordLabel = new Label("Mật khẩu");
        passwordLabel.getStyleClass().add("field-label");
        passwordLabel.setLabelFor(password);

        // Xóa trắng ô mật khẩu ngay trước khi gửi đi để mật khẩu không nằm lại trên màn hình nếu đăng nhập hỏng.
        Button login = new Button("Đăng nhập");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.setTooltip(new Tooltip("Đăng nhập vào ứng dụng (Enter)"));
        login.disableProperty().bind(tasks.busyProperty().or(identifier.textProperty().isEmpty())
                .or(password.textProperty().isEmpty()));
        login.setOnAction(event -> {
            String secret = password.getText();
            password.clear();
            controller.login(identifier.getText(), secret, onSuccess);
        });

        // Vòng xoay nhỏ chỉ hiện trong lúc đang kiểm tra mật khẩu ở luồng nền.
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        Label loadingLabel = new Label("Đang kiểm tra đăng nhập...");
        loadingLabel.getStyleClass().add("muted-text");
        HBox loadingRow = new HBox(8, spinner, loadingLabel);
        loadingRow.setAlignment(Pos.CENTER);
        loadingRow.visibleProperty().bind(tasks.busyProperty());
        loadingRow.managedProperty().bind(loadingRow.visibleProperty());

        VBox form = new VBox(16, heading,
                new VBox(6, identifierLabel, identifier),
                new VBox(6, passwordLabel, password),
                login,
                loadingRow);
        form.getStyleClass().add("login-card");
        form.setMaxWidth(420);
        form.setMaxHeight(USE_PREF_SIZE);
        form.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(form);
    }
}
