package com.dippie.mail;

// Gói nội dung một bức thư gửi đi (người nhận, tiêu đề, thân thư) dưới dạng bất biến.
public record EmailMessage(String to, String subject, String body) {

    // Lọc bỏ ký tự xuống dòng ở to và subject để chặn chèn thêm header email trái phép.
    public EmailMessage {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email người nhận không được để trống.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Tiêu đề email không được để trống.");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Nội dung email không được để trống.");
        }
        to = sanitizeHeader(to);
        subject = sanitizeHeader(subject);
    }

    private static String sanitizeHeader(String raw) {
        return raw.replace("\r", "").replace("\n", "").trim();
    }
}
