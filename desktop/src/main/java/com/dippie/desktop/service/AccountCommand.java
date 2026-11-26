package com.dippie.desktop.service;

import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;

// lệnh kiểu Command bọc thao tác khóa, mở khóa hoặc đổi vai trò, giữ cả bản chụp trước và sau để hoàn tác
public final class AccountCommand implements Command {
    private final UserService userService;
    private final AdminSession actor;
    private final UserAccount before;
    private final Role role;
    private final boolean enabled;
    private UserAccount after;

    public AccountCommand(UserService userService, AdminSession actor, UserAccount before, Role role, boolean enabled) {
        this.userService = userService;
        this.actor = actor;
        this.before = before;
        this.role = role;
        this.enabled = enabled;
    }

    @Override
    public void execute() {
        after = userService.change(actor, before, role, enabled);
    }

    // hoàn tác bằng transaction mới có ghi audit log và so với bản chụp after kẻo ghi đè thay đổi mới hơn
    @Override
    public void undo() {
        userService.change(actor, after, before.role(), before.enabled());
    }
}
