package com.dippie.desktop.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

// xuất dữ liệu ra file CSV chuẩn UTF-8, cấu trúc cột từng bảng tách sang CsvStrategy kiểu Strategy
public final class CsvExporter {
    // ghi ra file tạm cùng thư mục rồi mới thay file đích để lỡ lỗi giữa chừng không hỏng file cũ
    // có thêm BOM (\uFEFF) đầu file để Excel mở tiếng Việt khỏi vỡ dấu
    public <T> void write(Path destination, CsvStrategy<T> strategy, List<T> rows) throws IOException {
        Path target = destination.toAbsolutePath();
        Path tempFile = Files.createTempFile(target.getParent(), "dippie-", ".csv.tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                writeRow(writer, strategy.headers());
                for (T row : rows) {
                    writeRow(writer, strategy.cells().apply(row));
                }
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ghi từng ô ngăn bằng dấu phẩy và xuống dòng CRLF cho đúng chuẩn Excel
    private static void writeRow(BufferedWriter writer, List<?> cells) throws IOException {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escape(cells.get(index)));
        }
        writer.write("\r\n");
    }

    // thêm dấu nháy đơn trước chuỗi mở đầu bằng =, +, -, @ hoặc xuống dòng kẻo Excel chạy thành công thức
    private static String escape(Object cell) {
        String text = cell == null ? "" : cell.toString();
        String trimmed = text.stripLeading();
        if (cell instanceof CharSequence && ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0))) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
