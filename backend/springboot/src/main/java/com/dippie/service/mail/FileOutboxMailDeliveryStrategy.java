package com.dippie.service.mail;

import com.dippie.config.mail.MailProperties;
import com.dippie.model.mail.EmailMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// nhánh Strategy xuất mail ra file .eml trên đĩa thay vì SMTP thật, tiện lúc chạy dev, demo với test
public class FileOutboxMailDeliveryStrategy implements MailDeliveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(FileOutboxMailDeliveryStrategy.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final MailProperties properties;
    private final Clock clock;

    public FileOutboxMailDeliveryStrategy(MailProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // tạo thư mục outbox nếu chưa có, xuất file .eml UTF-8 rồi ghi đúng 1 dòng log
    @Override
    public void deliver(EmailMessage message) {
        Instant now = clock.instant();
        Path directory = properties.outboxPath();
        try {
            Files.createDirectories(directory);
            Path outputFile = resolveUniqueFilePath(directory, now, message.to());
            String emlContent = formatEml(message, now);
            Files.writeString(
                    outputFile,
                    emlContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            // Việt đừng nhét email người nhận vào log chỗ này nhé, chỉ in thư mục kẻo lọt PII ra file log
            log.info("Đã lưu một email mới vào thư mục {}", directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu tệp email vào thư mục hộp thư.", ex);
        }
    }

    // format theo chuẩn RFC 5322 kèm UTF-8 để mở thẳng trên app mail không bị lỗi dấu tiếng Việt
    private String formatEml(EmailMessage message, Instant sentAt) {
        String rfcDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(sentAt.atOffset(ZoneOffset.UTC));
        return "From: " + properties.from() + "\r\n"
                + "To: " + message.to() + "\r\n"
                + "Date: " + rfcDate + "\r\n"
                + "Subject: " + message.subject() + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n"
                + "\r\n"
                + message.body();
    }

    // đặt tên file <timestamp>-<recipient>.eml, lỡ 2 mail gửi trùng mili giây thì gắn thêm đuôi số kẻo ghi đè
    private static Path resolveUniqueFilePath(Path directory, Instant now, String recipient) {
        String timestamp = FILE_TIMESTAMP.format(now);
        String safeRecipient = sanitizeFilenameSegment(recipient);
        String baseName = timestamp + "-" + safeRecipient;
        Path candidate = directory.resolve(baseName + ".eml");
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + "-" + suffix + ".eml");
            suffix++;
        }
        return candidate;
    }

    // lọc ký tự cấm trên Windows và chặn ".." kẻo nó ghi trồi ra ngoài thư mục outbox
    private static String sanitizeFilenameSegment(String recipient) {
        String cleaned = recipient.replaceAll("[^a-zA-Z0-9._@-]", "_").replace("..", "_");
        return cleaned.isBlank() ? "recipient" : cleaned;
    }
}
