package com.dippie.desktop;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.LoginController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.db.DatabaseFactory;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.service.AdminServices;
import com.dippie.desktop.view.HeaderBar;
import com.dippie.desktop.view.LoginPane;
import com.dippie.desktop.view.StatusBar;
import com.dippie.desktop.view.WorkspacePane;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

// Điểm vào JavaFX của bản quản trị, dựng khung cửa sổ rồi đổi giữa màn hình đăng nhập và màn hình chính.
public final class DippieAdminApp extends Application {
    private final BorderPane shell = new BorderPane();
    private UiTasks tasks;
    private HeaderBar header;
    private StatusBar statusBar;
    private AdminServices services;
    private WorkspacePane workspace;

    // Mở cửa sổ trước rồi mới đọc cấu hình DB, lỡ lỗi cấu hình thì vẫn có khung báo cho người dùng thấy.
    @Override
    public void start(Stage stage) {
        tasks = new UiTasks(stage);
        header = new HeaderBar(tasks, this::showLogin);
        statusBar = new StatusBar(tasks);
        shell.setTop(header);
        shell.setBottom(statusBar);

        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/desktop.css").toExternalForm());
        stage.setTitle("Dippie Admin");
        stage.setMinWidth(960);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();

        try {
            services = new AdminServices(new JdbcDatabase(DatabaseFactory.load(moduleDirectory())));
            showLogin();
        } catch (DataAccessException failure) {
            shell.setCenter(configurationErrorPane());
            tasks.configurationError(failure);
        }
    }

    // Việt nhớ chỗ này: phải gọi workspace.stop() để tắt timer 30 giây ở tab Tổng quan,
    // không là nó vẫn âm thầm gọi DB sau khi đăng xuất.
    private void showLogin() {
        if (workspace != null) {
            workspace.stop();
            workspace = null;
        }
        header.clearSignedIn();
        statusBar.setShortcutsVisible(false);
        shell.setCenter(new LoginPane(new LoginController(services, tasks), tasks, this::openWorkspace));
    }

    // Tạo controller cho phiên vừa đăng nhập, mở các tab rồi tải tên quản trị viên lên thanh trên cùng.
    private void openWorkspace(AdminSession session) {
        var controller = new AdminController(services, tasks, session);
        header.showLoadingSession();
        statusBar.setShortcutsVisible(true);
        workspace = new WorkspacePane(tasks, controller);
        shell.setCenter(workspace);
        controller.currentAdmin(header::showSignedIn);
    }

    // Màn hình thay thế khi khởi động mà đọc cấu hình kết nối bị lỗi.
    private static StackPane configurationErrorPane() {
        Label title = new Label("Chưa kết nối được cơ sở dữ liệu");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Kiểm tra lại tệp desktop.properties hoặc dữ liệu demo rồi mở lại Dippie Admin.");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        VBox card = new VBox(8, title, hint);
        card.getStyleClass().add("surface-card");
        card.setMaxWidth(480);
        card.setMaxHeight(StackPane.USE_PREF_SIZE);
        StackPane wrapper = new StackPane(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.getStyleClass().add("login-canvas");
        return wrapper;
    }

    // Ưu tiên đường dẫn Maven truyền vào; nếu chạy trực tiếp từ IDE thì tự nhận đang đứng ở thư mục gốc hay trong desktop/.
    private static Path moduleDirectory() {
        String configured = System.getProperty("dippie.desktop.module");
        if (configured != null) {
            return Path.of(configured);
        }
        Path current = Path.of("").toAbsolutePath();
        return Files.isRegularFile(current.resolve("mvnw.cmd")) ? current : current.resolve("desktop");
    }

    @Override
    public void stop() {
        if (workspace != null) {
            workspace.stop();
        }
        if (tasks != null) {
            tasks.close();
        }
    }

    public static void main(String[] arguments) {
        launch(arguments);
    }
}
