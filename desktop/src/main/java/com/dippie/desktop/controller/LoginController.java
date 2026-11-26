package com.dippie.desktop.controller;

import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.service.AdminServices;
import com.dippie.desktop.service.AuthException;
import com.dippie.desktop.service.AuthService;
import javafx.scene.control.Alert;

import java.util.function.Consumer;

// controller màn hình đăng nhập (MVC), nối form giao diện với AuthService
public final class LoginController {
    private final AdminServices services;
    private final UiTasks tasks;

    public LoginController(AdminServices services, UiTasks tasks) {
        this.services = services;
        this.tasks = tasks;
    }

    // check đầu vào ngay đây để gõ thiếu hay quá 72 byte thì báo liền, khỏi bật trạng thái đang kết nối DB
    public void login(String identifier, String password, Consumer<AdminSession> onSuccess) {
        try {
            AuthService.validateInput(identifier, password);
        } catch (AuthException exception) {
            tasks.message(Alert.AlertType.ERROR, "Đăng nhập chưa thành công", exception.getMessage());
            return;
        }
        tasks.database(() -> services.login(identifier, password), onSuccess);
    }
}
