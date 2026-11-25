package com.dippie.desktop.service;

// Hợp đồng Command cho các thao tác quản trị có thể hoàn tác.
public interface Command {
    void execute();

    void undo();
}
