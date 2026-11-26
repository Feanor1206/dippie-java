package com.dippie.model.mail;

// gói bất biến nội dung một email gửi đi (người nhận, tiêu đề, thân thư)
public record EmailMessage(String to, String subject, String body) {

    // chặn ký tự xuống dòng trong to với subject kẻo bị chèn header email bậy
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
