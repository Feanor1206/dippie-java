package com.dippie.desktop.db;

// lớp cha trừu tượng cho mọi lỗi đụng DB bên desktop
// UiTasks bắt chung kiểu này để hiện thông báo tiếng Việt thay vì lộ lỗi SQL (kế thừa và đa hình)
public abstract class DataAccessException extends RuntimeException {
    protected DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    // lỗi đọc cấu hình hoặc không kết nối được DB
    public static final class Connection extends DataAccessException {
        public Connection(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // lỗi SQL lúc đang đọc dữ liệu
    public static final class Query extends DataAccessException {
        public Query(Throwable cause) {
            super("Không đọc được dữ liệu từ cơ sở dữ liệu. Kiểm tra lại cấu trúc bảng và quyền truy cập.", cause);
        }
    }

    // lỗi SQL văng ra giữa transaction ghi
    public static final class Transaction extends DataAccessException {
        public Transaction(Throwable cause) {
            super("Không lưu được thay đổi. Hãy làm mới dữ liệu rồi thử lại.", cause);
        }
    }

    // dữ liệu vừa bị phiên khác sửa hoặc làm trái quy tắc quản trị
    public static final class Conflict extends DataAccessException {
        public Conflict(String message) {
            super(message, null);
        }
    }
}
