package com.dippie.desktop.service;

// interface Command cho mấy thao tác quản trị hoàn tác được
public interface Command {
    void execute();

    void undo();
}
