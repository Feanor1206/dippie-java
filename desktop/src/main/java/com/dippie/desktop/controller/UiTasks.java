package com.dippie.desktop.controller;

import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.service.AuthException;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Chạy mọi tác vụ đụng DB và ghi tệp trên một luồng nền rồi báo kết quả về luồng JavaFX.
// Việt đừng đổi worker sang thread pool nhiều luồng nha, xếp hàng tuần tự thế này để lệnh sửa tài khoản với hoàn tác không bị chạy lệch thứ tự.
public final class UiTasks implements AutoCloseable {
    private static final String STYLESHEET = stylesheetUrl();
    private final Window owner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "dippie-desktop-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<ConnectionState> connection =
            new ReadOnlyObjectWrapper<>(ConnectionState.NOT_CHECKED);
    private int pending;
    private boolean closed;

    public UiTasks(Window owner) {
        this.owner = owner;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<ConnectionState> connectionProperty() {
        return connection.getReadOnlyProperty();
    }

    public Window owner() {
        return owner;
    }

    public <T> void database(Callable<T> action, Consumer<T> onSuccess) {
        database(action, onSuccess, () -> {
        });
    }

    // Nhận thêm onComplete để tắt vòng quay tải kể cả khi truy vấn bị lỗi.
    public <T> void database(Callable<T> action, Consumer<T> onSuccess, Runnable onComplete) {
        connection.set(connection.get().onRequest());
        submit(action, onSuccess, onComplete, true);
    }

    public <T> void file(Callable<T> action, Consumer<T> onSuccess) {
        submit(action, onSuccess, () -> {
        }, false);
    }

    // Đẩy việc vào hàng đợi nền bằng lớp ẩn danh kế thừa Task để tự chuyển kết quả về luồng JavaFX.
    private <T> void submit(Callable<T> action, Consumer<T> onSuccess, Runnable onComplete, boolean isDatabase) {
        if (closed) {
            onComplete.run();
            return;
        }
        pending++;
        busy.set(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.call();
            }
        };
        task.setOnSucceeded(event -> finish(() -> {
            if (isDatabase) {
                connection.set(ConnectionState.CONNECTED);
            }
            onSuccess.accept(task.getValue());
        }, onComplete));
        task.setOnFailed(event -> finish(() -> showFailure(task.getException(), isDatabase), onComplete));
        worker.execute(task);
    }

    // Giữ trạng thái bận tới khi cả onSuccess lẫn onComplete chạy xong, tránh mở khóa nút sớm khi tab đang tự tải lại.
    private void finish(Runnable action, Runnable onComplete) {
        if (closed) {
            return;
        }
        try {
            action.run();
        } finally {
            try {
                onComplete.run();
            } finally {
                pending--;
                busy.set(pending > 0);
            }
        }
    }

    // Chỉ hiện thông báo tiếng Việt đã chuẩn bị sẵn, không làm lộ chi tiết JDBC hay chuỗi kết nối ra hộp thoại.
    private void showFailure(Throwable failure, boolean isDatabase) {
        if (isDatabase) {
            connection.set(isDisconnected(failure) ? ConnectionState.DISCONNECTED : ConnectionState.CONNECTED);
        }
        if (failure instanceof DataAccessException || failure instanceof AuthException) {
            message(Alert.AlertType.ERROR, "Có lỗi xảy ra", failure.getMessage());
        } else if (failure instanceof IOException) {
            message(Alert.AlertType.ERROR, "Chưa xuất được tệp CSV", "Kiểm tra lại tên tệp và quyền lưu vào thư mục này rồi thử lại.");
        } else {
            message(Alert.AlertType.ERROR, "Có lỗi xảy ra", "Thao tác chưa hoàn tất được. Hãy thử lại.");
        }
    }

    public void configurationError(DataAccessException failure) {
        showFailure(failure, true);
    }

    // Phân biệt mất kết nối thật (mã SQLState đầu 08 hoặc lỗi Connection) với lỗi truy vấn bình thường để chấm tròn không báo đỏ nhầm.
    private static boolean isDisconnected(Throwable failure) {
        if (failure instanceof DataAccessException.Connection) {
            return true;
        }
        return failure.getCause() instanceof SQLException sql && sql.getSQLState() != null
                && sql.getSQLState().startsWith("08");
    }

    // Hộp thoại xác nhận thường với nút chính màu xanh.
    public boolean confirm(String title, String content) {
        var confirm = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        return alert(Alert.AlertType.CONFIRMATION, title, content, false, confirm, cancel)
                .showAndWait().orElse(cancel) == confirm;
    }

    // Hộp thoại xác nhận nút đỏ cho thao tác khó rút lại như khóa tài khoản hoặc xóa đánh giá.
    public boolean confirmDanger(String title, String content, String confirmLabel) {
        var confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        return alert(Alert.AlertType.CONFIRMATION, title, content, true, confirm, cancel)
                .showAndWait().orElse(cancel) == confirm;
    }

    public void message(Alert.AlertType type, String title, String content) {
        var close = new ButtonType("Đóng", ButtonBar.ButtonData.OK_DONE);
        alert(type, title, content, type == Alert.AlertType.ERROR, close).showAndWait();
    }

    // Gắn chung CSS và kiểu nút cho mọi hộp thoại trong ứng dụng.
    public void styleDialog(Dialog<?> dialog, boolean destructive) {
        DialogPane pane = dialog.getDialogPane();
        if (STYLESHEET != null && !pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
        if (!pane.getStyleClass().contains("app-dialog")) {
            pane.getStyleClass().add("app-dialog");
        }
        pane.setMinWidth(400);
        for (ButtonType buttonType : pane.getButtonTypes()) {
            Node button = pane.lookupButton(buttonType);
            if (button == null) {
                continue;
            }
            var buttonData = buttonType.getButtonData();
            if (buttonData == ButtonBar.ButtonData.OK_DONE || buttonData == ButtonBar.ButtonData.YES
                    || buttonData == ButtonBar.ButtonData.APPLY) {
                button.getStyleClass().add(destructive ? "danger-button" : "primary-button");
            } else if (buttonData == ButtonBar.ButtonData.CANCEL_CLOSE || buttonData == ButtonBar.ButtonData.NO) {
                button.getStyleClass().add("secondary-button");
            }
        }
    }

    private Alert alert(Alert.AlertType type, String title, String content, boolean destructive, ButtonType... buttons) {
        var alert = new Alert(type, content, buttons);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert, destructive);
        return alert;
    }

    private static String stylesheetUrl() {
        URL resource = UiTasks.class.getResource("/desktop.css");
        return resource == null ? null : resource.toExternalForm();
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
    }
}
