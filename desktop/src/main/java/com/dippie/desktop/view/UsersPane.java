package com.dippie.desktop.view;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.export.CsvStrategy;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.SearchText;
import com.dippie.desktop.model.UserAccount;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.List;

// tab Người dùng (kế thừa DataPane), lọc thẳng trên FilteredList rồi đổi quyền, khóa hay hoàn tác qua Command
public final class UsersPane extends DataPane {
    private final AdminController controller;
    private final ObservableList<UserAccount> accounts = FXCollections.observableArrayList();
    private final FilteredList<UserAccount> filtered = new FilteredList<>(accounts);
    private final TableView<UserAccount> users;
    private final TextField search = new TextField();
    private final Label resultCount = pill("0 tài khoản", "pill-neutral");

    public UsersPane(UiTasks tasks, AdminController controller) {
        super(tasks);
        this.controller = controller;
        var sorted = new SortedList<>(filtered);
        users = table(sorted, "Chưa có tài khoản nào",
                "Danh sách thành viên sẽ hiện ở đây sau khi tải xong.");
        sorted.comparatorProperty().bind(users.comparatorProperty());

        // dựng bảng tài khoản kèm nhãn màu cho vai trò và trạng thái hoạt động
        users.getColumns().add(numericColumn("Mã", 64, UserAccount::id));
        users.getColumns().add(column("Tên đăng nhập", 145, UserAccount::username));
        users.getColumns().add(column("Họ và tên", 170,
                row -> row.fullName() == null || row.fullName().isBlank() ? "-" : row.fullName()));
        users.getColumns().add(column("Email", 215, UserAccount::email));

        var roleColumn = column("Vai trò", 140, UserAccount::role);
        roleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(pill(role.toString(), role == Role.ROLE_ADMIN ? "pill-honey" : "pill-neutral"));
            }
        });
        users.getColumns().add(roleColumn);

        var statusColumn = column("Trạng thái", 145, UserAccount::enabled);
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean enabled, boolean empty) {
                super.updateItem(enabled, empty);
                if (empty || enabled == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(pill(enabled ? "Đang hoạt động" : "Đã khóa", enabled ? "pill-sage" : "pill-danger"));
            }
        });
        users.getColumns().add(statusColumn);

        // ô tìm tức thời theo username, họ tên hoặc email (hỗ trợ không dấu)
        search.setPromptText("Tìm tên đăng nhập, họ tên, email... (Ctrl+F)");
        search.getStyleClass().add("search-field");
        search.setTooltip(new Tooltip("Tìm theo tên đăng nhập, họ tên hoặc email (Ctrl+F)"));
        Label label = new Label("Tìm người dùng");
        label.getStyleClass().add("field-label");
        label.setLabelFor(search);

        Button clearFilter = new Button("Xóa lọc");
        clearFilter.getStyleClass().add("ghost-button");
        clearFilter.setTooltip(new Tooltip("Xóa từ khóa đang tìm"));
        clearFilter.visibleProperty().bind(search.textProperty().isNotEmpty());
        clearFilter.managedProperty().bind(clearFilter.visibleProperty());
        clearFilter.setOnAction(event -> {
            search.clear();
            search.requestFocus();
        });

        search.textProperty().addListener((property, previous, text) -> applyFilter());

        var strategy = new CsvStrategy<UserAccount>(
                List.of("Mã", "Tên đăng nhập", "Họ và tên", "Email", "Vai trò", "Trạng thái"),
                row -> List.of(row.id(), row.username(), row.fullName() == null ? "" : row.fullName(), row.email(),
                        row.role().toString(), row.statusLabel()));

        var undo = new Button("Hoàn tác");
        undo.getStyleClass().add("secondary-button");
        undo.setTooltip(new Tooltip("Quay lại trạng thái trước lần chỉnh sửa tài khoản gần nhất (Ctrl+Z)"));
        undo.disableProperty().bind(tasks.busyProperty().or(controller.undoAvailableProperty().not()));
        undo.setOnAction(event -> triggerUndo());

        setTop(toolbar(label, search, clearFilter, resultCount, refreshButton(),
                exportButton(users, strategy, "nguoi-dung.csv"), roleButton(), statusButton(), undo));
        setContent(users);
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    // dùng chung cho cả nút Hoàn tác lẫn Ctrl+Z, luôn hỏi xác nhận trước khi chạy
    public void triggerUndo() {
        if (tasks.busyProperty().get() || !controller.undoAvailableProperty().get()) {
            return;
        }
        if (tasks.confirm("Hoàn tác", "Bạn muốn hoàn tác lần chỉnh sửa tài khoản gần nhất?")) {
            controller.undo();
        }
    }

    // tải lại danh sách từ DB rồi chọn lại đúng tài khoản cũ theo id nếu còn trong bảng
    @Override
    public void refresh() {
        setLoading(true);
        var selected = users.getSelectionModel().getSelectedItem();
        controller.users(rows -> {
            accounts.setAll(rows);
            applyFilter();
            if (selected != null) {
                users.getItems().stream().filter(row -> row.id() == selected.id()).findFirst()
                        .ifPresent(users.getSelectionModel()::select);
            }
        }, () -> setLoading(false));
    }

    // cập nhật bộ lọc từ khóa, bộ đếm kết quả và thông báo khi bảng trống
    private void applyFilter() {
        String term = SearchText.normalize(search.getText());
        filtered.setPredicate(row -> SearchText.contains(row.username(), term)
                || SearchText.contains(row.fullName(), term) || SearchText.contains(row.email(), term));
        int visible = filtered.size();
        int total = accounts.size();
        resultCount.setText(term.isEmpty() ? total + " tài khoản" : visible + " / " + total + " tài khoản");
        users.setPlaceholder(term.isEmpty()
                ? emptyPlaceholder("Chưa có tài khoản nào", "Hiện chưa có tài khoản nào trong hệ thống.")
                : emptyPlaceholder("Không thấy tài khoản nào khớp", "Thử từ khóa khác hoặc bấm Xóa lọc để xem tất cả."));
    }

    // tự đổi chữ và màu giữa Khóa tài khoản (đỏ) với Mở khóa (xanh) theo dòng đang chọn
    private Button statusButton() {
        var selection = users.getSelectionModel().selectedItemProperty();
        var button = new Button();
        button.getStyleClass().add("danger-button");
        button.setTooltip(new Tooltip("Khóa hoặc mở khóa tài khoản đang chọn (không dùng cho chính mình)"));
        button.textProperty().bind(Bindings.createStringBinding(
                () -> selection.get() != null && !selection.get().enabled() ? "Mở khóa" : "Khóa tài khoản",
                selection));
        selection.addListener((property, before, current) -> {
            boolean isLocked = current != null && !current.enabled();
            button.getStyleClass().removeAll("danger-button", "primary-button");
            button.getStyleClass().add(isLocked ? "primary-button" : "danger-button");
        });
        bindAction(button);
        button.setOnAction(event -> {
            var selected = selection.get();
            if (selected.enabled()) {
                if (tasks.confirmDanger("Khóa tài khoản",
                        "Khóa tài khoản \"" + selected.username() + "\"? Người này sẽ bị đăng xuất khỏi các phiên đang mở.",
                        "Khóa tài khoản")) {
                    controller.changeAccount(selected, selected.role(), false);
                }
            } else if (tasks.confirm("Mở khóa tài khoản",
                    "Mở khóa tài khoản \"" + selected.username() + "\" để người này đăng nhập lại được?")) {
                controller.changeAccount(selected, selected.role(), true);
            }
        });
        return button;
    }

    // mở hộp chọn vai trò, chỉ gửi lệnh khi chọn vai trò khác hiện tại
    private Button roleButton() {
        var button = new Button("Đổi vai trò");
        button.getStyleClass().add("secondary-button");
        button.setTooltip(new Tooltip("Đổi vai trò của tài khoản đang chọn (không dùng cho chính mình)"));
        bindAction(button);
        button.setOnAction(event -> {
            var selected = users.getSelectionModel().getSelectedItem();
            var dialog = new ChoiceDialog<>(selected.role(), Role.values());
            if (tasks.owner() != null) {
                dialog.initOwner(tasks.owner());
            }
            dialog.setTitle("Đổi vai trò");
            dialog.setHeaderText("Tài khoản \"" + selected.username() + "\"");
            dialog.setContentText("Chọn vai trò mới");
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Xác nhận");
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Hủy");
            tasks.styleDialog(dialog, false);
            dialog.showAndWait().filter(role -> role != selected.role())
                    .ifPresent(role -> controller.changeAccount(selected, role, selected.enabled()));
        });
        return button;
    }

    // khóa nút thao tác khi chưa chọn dòng, đang chạy nền, hoặc dòng đang chọn là chính tài khoản đang đăng nhập
    private void bindAction(Button button) {
        var selection = users.getSelectionModel().selectedItemProperty();
        var unavailable = Bindings.createBooleanBinding(
                () -> selection.get() == null || selection.get().id() == controller.adminId(), selection);
        button.disableProperty().bind(tasks.busyProperty().or(unavailable));
    }
}
