package com.dippie.desktop.db;

// Lớp cha trừu tượng cho mọi lỗi đụng DB trong bản desktop.
// UiTasks bắt chung kiểu này để hiện câu báo tiếng Việt thay vì lộ lỗi SQL (kế thừa và đa hình ở đây).
public abstract class DataAccessException extends RuntimeException {
    protected DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    // Lỗi đọc cấu hình hoặc không mở được kết nối tới DB.
    public static final class Connection extends DataAccessException {
        public Connection(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Lỗi SQL khi đang đọc dữ liệu.
    public static final class Query extends DataAccessException {
        public Query(Throwable cause) {
            super("Không đọc được dữ liệu từ cơ sở dữ liệu. Kiểm tra lại cấu trúc bảng và quyền truy cập.", cause);
        }
    }

    // Lỗi SQL xảy ra giữa một giao dịch ghi.
    public static final class Transaction extends DataAccessException {
        public Transaction(Throwable cause) {
            super("Không lưu được thay đổi. Hãy làm mới dữ liệu rồi thử lại.", cause);
        }
    }

    // Lỗi khi dữ liệu vừa bị phiên khác sửa hoặc thao tác trái quy tắc quản trị.
    public static final class Conflict extends DataAccessException {
        public Conflict(String message) {
            super(message, null);
        }
    }
}
