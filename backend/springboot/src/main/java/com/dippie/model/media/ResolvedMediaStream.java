package com.dippie.model.media;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// gói đường dẫn file video đã check quyền kèm hàm đổ từng khoảng byte ra HTTP
public record ResolvedMediaStream(Path path, long totalLength, MediaType mediaType) {

    private static final int CHUNK_SIZE = 64 * 1024;

    // ghi đồng bộ từng khúc 64KB bằng FileChannel thay vì async stream, kẻo video dài bị Spring cắt ngang vì async timeout
    public void writeRange(long start, long length, OutputStream outputStream) throws IOException {
        if (length <= 0) {
            return;
        }
        byte[] buffer = new byte[CHUNK_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(start);
            long remaining = length;
            // chặn limit của buffer theo số byte còn lại kẻo đọc lố ra ngoài Range khách xin
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
