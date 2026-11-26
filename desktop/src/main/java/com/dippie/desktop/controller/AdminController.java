package com.dippie.desktop.controller;

import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.DashboardSnapshot;
import com.dippie.desktop.model.RatingRow;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;
import com.dippie.desktop.model.catalog.SeriesNode;
import com.dippie.desktop.service.AdminServices;
import com.dippie.desktop.service.CatalogService;
import com.dippie.desktop.service.CommandHistory;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

import java.util.List;
import java.util.function.Consumer;

// controller chính của 4 tab quản trị (MVC), dùng JavaFX property làm Observer
// để sửa dữ liệu xong là báo mấy tab liên quan tự tải lại
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

    // lấy thông tin admin đang đăng nhập để hiện tên lên thanh trên cùng
    public void currentAdmin(Consumer<UserAccount> onSuccess) {
        tasks.database(() -> services.currentAdmin(session), onSuccess);
    }

    public void dashboard(Consumer<DashboardSnapshot> onSuccess) {
        dashboard(onSuccess, () -> {
        });
    }

    public void dashboard(Consumer<DashboardSnapshot> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.dashboard(session), onSuccess, onComplete);
    }

    public void catalog(Consumer<List<SeriesNode>> onSuccess) {
        catalog(onSuccess, () -> {
        });
    }

    public void catalog(Consumer<List<SeriesNode>> onSuccess, Runnable onComplete) {
        tasks.database(() -> services.catalog(session), onSuccess, onComplete);
    }

    // lọc cây danh mục đi qua controller để CatalogPane khỏi gọi thẳng CatalogService
    public List<SeriesNode> filterCatalog(List<SeriesNode> catalog, String query) {
        return CatalogService.filter(catalog, query);
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

    // chỉ lưu lệnh vào CommandHistory khi transaction chạy xong, lỡ lỗi thì lịch sử hoàn tác cũ vẫn còn nguyên
    public void changeAccount(UserAccount target, Role role, boolean enabled) {
        tasks.database(() -> {
            history.execute(services.accountCommand(session, target, role, enabled));
            return history.canUndo();
        }, this::accountChanged);
    }

    // chạy hoàn tác trên cùng hàng đợi nền kẻo lệch thứ tự với lệnh sửa tài khoản ngay trước đó
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
