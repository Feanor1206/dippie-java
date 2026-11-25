package com.dippie.desktop.controller;

import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;
import com.dippie.desktop.model.catalog.SeriesNode;
import com.dippie.desktop.service.AdminServices;
import com.dippie.desktop.service.CommandHistory;
import com.dippie.desktop.service.DashboardService;
import com.dippie.desktop.service.RatingService.RatingRow;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

import java.util.List;
import java.util.function.Consumer;

// Controller chính cho 4 tab quản trị (MVC). Mình dùng JavaFX property làm Observer
// để báo các tab liên quan tự tải lại sau khi giao dịch sửa dữ liệu thành công.
public final class AdminController {
    private final AdminServices services;
    private final UiTasks tasks;
    private final AdminSession session;
    private final CommandHistory history = new CommandHistory();
    private final ReadOnlyBooleanWrapper undoAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyLongWrapper usersChanged = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper ratingsChanged = new ReadOnlyLongWrapper();

    public AdminController(AdminServices services, UiTasks tasks, AdminSession session) {
        this.services = services;
        this.tasks = tasks;
        this.session = session;
    }

    public long adminId() {
        return session.userId();
    }

    public ReadOnlyBooleanProperty undoAvailableProperty() {
        return undoAvailable.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty usersChangedProperty() {
        return usersChanged.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty ratingsChangedProperty() {
        return ratingsChanged.getReadOnlyProperty();
    }

    // Lấy thông tin quản trị viên đang đăng nhập để hiện tên trên thanh tiêu đề.
    public void currentAdmin(Consumer<UserAccount> onSuccess) {
        tasks.database(() -> services.currentAdmin(session), onSuccess);
    }

    public void dashboard(Consumer<DashboardService.Snapshot> onSuccess) {
        dashboard(onSuccess, () -> {
        });
    }

    public void dashboard(Consumer<DashboardService.Snapshot> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.dashboard(session), onSuccess, onComplete);
    }

    public void catalog(Consumer<List<SeriesNode>> onSuccess) {
        catalog(onSuccess, () -> {
        });
    }

    public void catalog(Consumer<List<SeriesNode>> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.catalog(session), onSuccess, onComplete);
    }

    public void users(Consumer<List<UserAccount>> onSuccess) {
        users(onSuccess, () -> {
        });
    }

    public void users(Consumer<List<UserAccount>> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.users(session), onSuccess, onComplete);
    }

    public void ratings(Consumer<List<RatingRow>> onSuccess) {
        ratings(onSuccess, () -> {
        });
    }

    public void ratings(Consumer<List<RatingRow>> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.ratings(session), onSuccess, onComplete);
    }

    // Lưu lệnh vào CommandHistory chỉ khi giao dịch thành công; nếu lỗi thì lịch sử hoàn tác cũ không bị ghi đè.
    public void changeAccount(UserAccount target, Role role, boolean enabled) {
        tasks.database(() -> {
            history.execute(services.accountCommand(session, target, role, enabled));
            return history.canUndo();
        }, this::accountChanged);
    }

    // Chạy hoàn tác trên cùng hàng đợi nền để không bị lệch thứ tự với thao tác sửa tài khoản ngay trước đó.
    public void undo() {
        tasks.database(() -> {
            history.undo();
            return history.canUndo();
        }, this::accountChanged);
    }

    public void deleteRating(RatingRow rating) {
        tasks.database(() -> {
            services.deleteRating(session, rating);
            return null;
        }, unused -> ratingsChanged.set(ratingsChanged.get() + 1));
    }

    private void accountChanged(boolean canUndo) {
        undoAvailable.set(canUndo);
        usersChanged.set(usersChanged.get() + 1);
    }
}
