package com.dippie.desktop.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

// Xuất dữ liệu ra tệp CSV chuẩn UTF-8. Cấu trúc cột của từng bảng tách sang CsvStrategy (mẫu Strategy).
public final class CsvExporter {
    // Ghi ra tệp tạm trong cùng thư mục rồi mới thay tệp đích để lỡ lỗi giữa chừng không làm hỏng tệp cũ.
    // Có ghi thêm BOM (\uFEFF) đầu tệp để Excel mở tiếng Việt không bị vỡ dấu.
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

    // Ghi từng ô cách nhau bằng dấu phẩy và xuống dòng CRLF cho khớp chuẩn Excel.
    private static void writeRow(BufferedWriter writer, List<?> cells) throws IOException {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escape(cells.get(index)));
        }
        writer.write("\r\n");
    }

    // Thêm dấu nháy đơn trước chuỗi mở đầu bằng =, +, -, @ hoặc ký tự xuống dòng để Excel không chạy nó như công thức.
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
