package com.dippie.desktop.service;

// Invoker của mẫu Command, chỉ giữ thao tác sửa tài khoản thành công gần nhất trong phiên này.
public final class CommandHistory {
    private Command last;

    public void execute(Command command) {
        command.execute();
        last = command;
    }

    public boolean canUndo() {
        return last != null;
    }

    // Chỉ xóa lịch sử sau khi giao dịch hoàn tác chạy xong; nếu bị lỗi thì giữ nguyên trạng thái.
    public void undo() {
        if (last != null) {
            last.undo();
            last = null;
        }
    }
}
