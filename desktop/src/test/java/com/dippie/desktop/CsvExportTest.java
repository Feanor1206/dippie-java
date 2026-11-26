package com.dippie.desktop;

import com.dippie.desktop.export.CsvExporter;
import com.dippie.desktop.export.CsvStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// test xuất CSV gồm BOM UTF-8, thoát dấu phẩy, ngoặc kép, xuống dòng và chặn công thức Excel
class CsvExportTest {
    @TempDir
    Path directory;

    @Test
    void writesUtf8BomAndEscapesCommaQuotesAndNewline() throws Exception {
        Path file = directory.resolve("người dùng.csv");
        var strategy = new CsvStrategy<String>(List.of("Tên", "Vai trò"), name -> List.of(name, "Người dùng"));
        new CsvExporter().write(file, strategy, List.of("Nguyễn, \"An\"\nDòng hai"));

        byte[] bytes = Files.readAllBytes(file);
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                java.util.Arrays.copyOf(bytes, 3));
        assertEquals("\uFEFFTên,Vai trò\r\n\"Nguyễn, \"\"An\"\"\nDòng hai\",Người dùng\r\n",
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void exportNeutralizesSpreadsheetFormulasInText() throws Exception {
        Path file = directory.resolve("đánh giá.csv");
        var strategy = new CsvStrategy<String>(List.of("Tên"), List::of);
        new CsvExporter().write(file, strategy, List.of("=1+1", "  @tên"));
        assertEquals("\uFEFFTên\r\n'=1+1\r\n'  @tên\r\n", Files.readString(file));
    }
}
