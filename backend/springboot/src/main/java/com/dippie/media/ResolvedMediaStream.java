package com.dippie.media;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.http.MediaType;

// Đóng gói đường dẫn tệp video đã qua kiểm tra quyền cùng hàm chép dữ liệu theo khoảng byte ra phản hồi HTTP.
public record ResolvedMediaStream(Path path, long totalLength, MediaType mediaType) {

    private static final int CHUNK_SIZE = 64 * 1024;

    // Ghi đồng bộ từng khúc 64KB bằng FileChannel thay vì async stream để video dài không bị Spring cắt giữa chừng do async timeout.
    void writeRange(long start, long length, OutputStream outputStream) throws IOException {
        if (length <= 0) {
            return;
        }
        byte[] buffer = new byte[CHUNK_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(start);
            long remaining = length;
            // Giới hạn buffer theo số byte còn lại để không đọc lố sang phần ngoài Range được yêu cầu.
            while (remaining > 0) {
                byteBuffer.clear();
                byteBuffer.limit((int) Math.min(CHUNK_SIZE, remaining));
                int bytesRead = channel.read(byteBuffer);
                if (bytesRead <= 0) {
                    break;
                }
                outputStream.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            outputStream.flush();
        }
    }
}
