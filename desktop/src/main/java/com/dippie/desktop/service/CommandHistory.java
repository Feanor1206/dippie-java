package com.dippie.desktop.service;

// kiểu Command (vai Invoker), chỉ giữ thao tác sửa tài khoản thành công gần nhất trong phiên này
public final class CommandHistory {
    private Command last;

    public void execute(Command command) {
        command.execute();
        last = command;
    }

    public boolean canUndo() {
        return last != null;
    }

    // chỉ xóa lịch sử sau khi transaction hoàn tác chạy xong, lỡ lỗi thì giữ nguyên trạng thái
    public void undo() {
        if (last != null) {
            last.undo();
            last = null;
        }
    }
}
