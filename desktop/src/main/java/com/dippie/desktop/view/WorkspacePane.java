package com.dippie.desktop.view;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.UiTasks;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

import java.util.List;

// Khung chính gom 4 tab quản trị, gắn Observer làm mới chéo giữa các tab và cài phím tắt Ctrl+F, Ctrl+Z, F5.
public final class WorkspacePane extends BorderPane {
    private static final KeyCombination SHORTCUT_SEARCH =
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SHORTCUT_UNDO =
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SHORTCUT_REFRESH = new KeyCodeCombination(KeyCode.F5);

    private final DashboardPane dashboard;
    private final UsersPane users;
    private final TabPane tabs;

    public WorkspacePane(UiTasks tasks, AdminController controller) {
        dashboard = new DashboardPane(tasks, controller);
        var catalog = new CatalogPane(tasks, controller);
        users = new UsersPane(tasks, controller);
        var ratings = new RatingsPane(tasks, controller);

        tabs = new TabPane(
                tab("Tổng quan", "Xem nhanh số liệu, bảng xếp hạng và biểu đồ điểm", dashboard),
                tab("Danh mục", "Xem danh sách bộ phim, mùa, tập và nguồn phát", catalog),
                tab("Người dùng", "Đổi vai trò và khóa hoặc mở khóa tài khoản", users),
                tab("Đánh giá", "Xem, lọc và xóa đánh giá của người dùng", ratings));
        tabs.getStyleClass().add("workspace-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);

        // Gắn phím tắt lên Scene lúc vào phiên và gỡ sạch khi rời Scene để đăng xuất rồi không bị rò listener.
        EventHandler<KeyEvent> shortcutFilter = event -> {
            if (SHORTCUT_SEARCH.match(event)) {
                DataPane active = activePane();
                if (active != null) {
                    active.focusSearch();
                    event.consume();
                }
            } else if (SHORTCUT_REFRESH.match(event)) {
                DataPane active = activePane();
                if (active != null && !tasks.busyProperty().get()) {
                    active.refresh();
                    event.consume();
                }
            }
        };
        EventHandler<KeyEvent> undoHandler = event -> {
            if (SHORTCUT_UNDO.match(event) && !isEditingText()) {
                users.triggerUndo();
                event.consume();
            }
        };
        sceneProperty().addListener((property, previous, current) -> {
            if (previous != null) {
                previous.removeEventFilter(KeyEvent.KEY_PRESSED, shortcutFilter);
                previous.removeEventHandler(KeyEvent.KEY_PRESSED, undoHandler);
            }
            if (current != null) {
                current.addEventFilter(KeyEvent.KEY_PRESSED, shortcutFilter);
                current.addEventHandler(KeyEvent.KEY_PRESSED, undoHandler);
            }
        });

        // Nghe Observer từ AdminController để chỉ tải lại những tab bị ảnh hưởng sau mỗi lần sửa.
        controller.usersChangedProperty().addListener((property, previous, changed) -> {
            users.refresh();
            dashboard.refresh();
        });
        controller.ratingsChangedProperty().addListener((property, previous, changed) -> {
            ratings.refresh();
            catalog.refresh();
            dashboard.refresh();
        });

        // Gọi refresh() qua lớp cha DataPane (đa hình) để nạp dữ liệu ban đầu cho cả 4 tab.
        List<DataPane> panes = List.of(dashboard, catalog, users, ratings);
        panes.forEach(DataPane::refresh);
    }

    // Chuyển nhanh sang tab theo chỉ số (tiện ích chụp ảnh màn hình cũng dùng hàm này).
    public void selectTab(int index) {
        tabs.getSelectionModel().select(index);
    }

    public void stop() {
        dashboard.stop();
    }

    private DataPane activePane() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected != null && selected.getContent() instanceof DataPane pane ? pane : null;
    }

    // Chặn ở đây để lúc Việt đang gõ trong ô tìm kiếm thì Ctrl+Z hoàn tác chữ, không bị nhảy sang hoàn tác tài khoản.
    private boolean isEditingText() {
        if (getScene() == null) {
            return false;
        }
        Node focused = getScene().getFocusOwner();
        return focused instanceof TextInputControl input && input.isEditable();
    }

    private static Tab tab(String title, String tooltipText, DataPane pane) {
        Tab tab = new Tab(title, pane);
        tab.setTooltip(new Tooltip(tooltipText));
        return tab;
    }
}
