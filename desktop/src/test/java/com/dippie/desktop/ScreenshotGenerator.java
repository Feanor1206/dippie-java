package com.dippie.desktop;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.LoginController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.db.DatabaseConfig;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.catalog.CatalogNode;
import com.dippie.desktop.service.AdminServices;
import com.dippie.desktop.view.HeaderBar;
import com.dippie.desktop.view.LoginPane;
import com.dippie.desktop.view.StatusBar;
import com.dippie.desktop.view.WorkspacePane;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import org.h2.tools.RunScript;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Tiện ích nằm ở thư mục test để chụp 5 ảnh màn hình chính từ H2 trong bộ nhớ (hoặc DB thật truyền qua tham số).
public final class ScreenshotGenerator {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;

    private ScreenshotGenerator() {
    }

    // Nạp schema và dữ liệu mẫu vào H2, bật JavaFX rồi chụp lần lượt 5 ảnh PNG vào thư mục đích.
    public static void main(String[] args) throws Exception {
        Path outputDir = args.length > 0 ? Path.of(args[0]) : Path.of("target", "screenshots");
        Files.createDirectories(outputDir);

        // Nếu có tham số thứ hai là URL JDBC thì chụp trên DB thật (như bản demo đang chạy), không thì dùng dữ liệu mẫu.
        boolean useLiveDatabase = args.length > 1;
        String url = useLiveDatabase ? args[1] : "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        JdbcDatabase db = new JdbcDatabase(new DatabaseConfig(url, "sa", ""));
        try (Connection keepAliveConnection = db.open()) {
            if (!useLiveDatabase) {
                loadScript(keepAliveConnection, "/schema.sql");
                loadScript(keepAliveConnection, "/fixtures.sql");
            }

            // Bật JavaFX rồi chụp lần lượt từng màn hình ở kích thước 1280x800.
            CompletableFuture<Void> ready = new CompletableFuture<>();
            try {
                Platform.startup(() -> ready.complete(null));
            } catch (IllegalStateException alreadyStarted) {
                ready.complete(null);
            }
            ready.get(15, TimeUnit.SECONDS);

            CompletableFuture<Void> done = new CompletableFuture<>();
            Platform.runLater(() -> captureAll(new AdminServices(db), outputDir, done));
            done.get(30, TimeUnit.SECONDS);
            System.out.println("Đã xuất 5 ảnh màn hình tại: " + outputDir.toAbsolutePath());
        } finally {
            Platform.exit();
        }
    }

    // Dựng cửa sổ giống ứng dụng thật rồi chụp lần lượt màn hình đăng nhập và 4 tab quản trị.
    private static void captureAll(AdminServices services, Path outputDir, CompletableFuture<Void> done) {
        UiTasks tasks = new UiTasks(null);
        HeaderBar header = new HeaderBar(tasks, () -> {
        });
        StatusBar statusBar = new StatusBar(tasks);
        BorderPane shell = new BorderPane();
        shell.setTop(header);
        shell.setBottom(statusBar);

        Scene scene = new Scene(shell, WIDTH, HEIGHT);
        scene.getStylesheets().add(ScreenshotGenerator.class.getResource("/desktop.css").toExternalForm());

        // Chụp màn hình đăng nhập trước khi vào không gian quản trị.
        shell.setCenter(new LoginPane(new LoginController(services, tasks), tasks, session -> {
        }));
        afterLayout(scene, () -> {
            try {
                saveSnapshot(scene, outputDir.resolve("login.png"));
            } catch (IOException failure) {
                tasks.close();
                done.completeExceptionally(failure);
                return;
            }

            // Mở sẵn các tab bằng tài khoản admin mẫu trong fixtures.sql.
            AdminSession session = new AdminSession(1L, 0L);
            AdminController controller = new AdminController(services, tasks, session);
            header.showLoadingSession();
            statusBar.setShortcutsVisible(true);
            WorkspacePane workspace = new WorkspacePane(tasks, controller);
            shell.setCenter(workspace);
            controller.currentAdmin(header::showSignedIn);

            // Chờ tất cả lượt đọc DB ban đầu chạy xong rồi mới chụp từng tab.
            waitUntilIdle(tasks, () -> captureWorkspaceTabs(scene, workspace, outputDir, tasks, done));
        });
    }

    // Chuyển lần lượt qua từng tab, chọn sẵn dòng mẫu để hình chụp có đủ chi tiết rồi xuất PNG.
    private static void captureWorkspaceTabs(Scene scene, WorkspacePane workspace, Path outputDir,
                                             UiTasks tasks, CompletableFuture<Void> done) {
        captureTab(scene, workspace, 0, () -> {
        }, outputDir.resolve("dashboard.png"), () -> captureTab(scene, workspace, 1, () -> selectFirstEpisode(workspace),
                outputDir.resolve("catalog.png"), () -> captureTab(scene, workspace, 2, () -> selectTableRow(workspace, 1),
                        outputDir.resolve("users.png"), () -> captureTab(scene, workspace, 3, () -> selectTableRow(workspace, 0),
                                outputDir.resolve("ratings.png"), () -> {
                                    workspace.stop();
                                    tasks.close();
                                    done.complete(null);
                                }, tasks, done), tasks, done), tasks, done), tasks, done);
    }

    private static void captureTab(Scene scene, WorkspacePane workspace, int tabIndex, Runnable prepare,
                                   Path target, Runnable next, UiTasks tasks, CompletableFuture<Void> done) {
        workspace.selectTab(tabIndex);
        afterLayout(scene, () -> {
            prepare.run();
            afterLayout(scene, () -> {
                try {
                    saveSnapshot(scene, target);
                    next.run();
                } catch (IOException failure) {
                    workspace.stop();
                    tasks.close();
                    done.completeExceptionally(failure);
                }
            });
        });
    }

    // Mở nhánh đầu tiên trên cây danh mục rồi chọn tập đầu tiên để khung chi tiết bên phải có đủ dữ liệu.
    @SuppressWarnings("unchecked")
    private static void selectFirstEpisode(WorkspacePane workspace) {
        Node node = workspace.lookup(".tree-view");
        if (!(node instanceof TreeView<?> rawTree)) {
            return;
        }
        TreeView<CatalogNode> tree = (TreeView<CatalogNode>) rawTree;
        TreeItem<CatalogNode> root = tree.getRoot();
        if (root == null || root.getChildren().isEmpty()) {
            return;
        }
        TreeItem<CatalogNode> series = root.getChildren().getFirst();
        series.setExpanded(true);
        if (!series.getChildren().isEmpty()) {
            TreeItem<CatalogNode> season = series.getChildren().getFirst();
            season.setExpanded(true);
            if (!season.getChildren().isEmpty()) {
                tree.getSelectionModel().select(season.getChildren().getFirst());
                return;
            }
            tree.getSelectionModel().select(season);
            return;
        }
        tree.getSelectionModel().select(series);
    }

    // Chọn sẵn một dòng trong bảng đang hiện để ảnh chụp thấy rõ nút thao tác đang bật.
    private static void selectTableRow(WorkspacePane workspace, int rowIndex) {
        for (Node node : workspace.lookupAll(".table-view")) {
            if (node instanceof TableView<?> table && table.isVisible() && table.getItems().size() > rowIndex) {
                table.getSelectionModel().select(rowIndex);
                return;
            }
        }
    }

    // Đợi hàng đợi nền của UiTasks chạy hết các truy vấn H2 rồi mới gọi onIdle.
    private static void waitUntilIdle(UiTasks tasks, Runnable onIdle) {
        if (!tasks.busyProperty().get()) {
            Platform.runLater(onIdle);
            return;
        }
        PauseTransition poll = new PauseTransition(Duration.millis(40));
        poll.setOnFinished(event -> waitUntilIdle(tasks, onIdle));
        poll.play();
    }

    // Ép chạy applyCss và layout hai nhịp cách nhau 120ms để bảng, cây và biểu đồ kịp vẽ xong trước khi chụp.
    private static void afterLayout(Scene scene, Runnable action) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        PauseTransition settle = new PauseTransition(Duration.millis(120));
        settle.setOnFinished(event -> {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            action.run();
        });
        settle.play();
    }

    // Chụp Scene bằng Scene.snapshot rồi tự chuyển sang BufferedImage để ghi PNG mà không cần thêm javafx-swing.
    private static void saveSnapshot(Scene scene, Path target) throws IOException {
        WritableImage image = scene.snapshot(null);
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(buffered, "png", target.toFile());
    }

    private static void loadScript(Connection keepAliveConnection, String resource) throws Exception {
        try (var reader = new InputStreamReader(ScreenshotGenerator.class.getResourceAsStream(resource),
                StandardCharsets.UTF_8)) {
            RunScript.execute(keepAliveConnection, reader);
        }
    }
}
