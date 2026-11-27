package com.dippie.desktop.db;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

// mấy cột giờ trong DB dùng chung với backend đều lưu giờ UTC không kèm múi giờ
// Việt nhớ ghi thì lấy now() ở đây chứ đừng dùng CURRENT_TIMESTAMP, cái đó ra giờ máy (+07) là lệch với bên web liền
public final class UtcTime {
    private UtcTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    // đổi giờ UTC đọc từ DB sang giờ máy đang mở app để hiện lên bảng cho đúng giờ Việt Nam
    public static LocalDateTime toLocal(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
