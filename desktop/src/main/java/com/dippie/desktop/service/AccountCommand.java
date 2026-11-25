package com.dippie.desktop.service;

import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;

// Lệnh Command bọc thao tác khóa, mở khóa hoặc đổi vai trò, giữ cả bản chụp trước và sau để hoàn tác.
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

    // Hoàn tác bằng giao dịch mới có ghi nhật ký và đối chiếu với bản chụp after, để không ghi đè thay đổi mới hơn.
    @Override
    public void undo() {
        userService.change(actor, after, before.role(), before.enabled());
    }
}
