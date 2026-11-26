package com.dippie.service.quiz;

import com.dippie.dto.quiz.QuizCsvPreviewResponse;
import com.dippie.dto.quiz.QuizCsvRowPreview;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// đọc và kiểm tra file CSV câu hỏi của phụ huynh: hỗ trợ UTF-8 BOM mở chuẩn bằng Excel, dấu phẩy trong ngoặc kép và báo lỗi từng dòng bằng tiếng Việt
@Component
public class QuizCsvParser {

    public static final String UTF8_BOM = "\uFEFF";

    public static final String TEMPLATE_CSV = UTF8_BOM
            + "cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop\r\n"
            + "\"7 × 8 bằng bao nhiêu?\",54,56,48,63,B,\"7 × 8 = 56 trong bảng nhân 7\",Toán,Lớp 3\r\n"
            + "\"What color is a ripe banana?\",Blue,Yellow,Red,Green,B,\"Yellow là màu vàng, giống quả chuối chín\",Tiếng Anh,Mẫu giáo\r\n"
            + "\"Bộ phận nào của cây hút nước, muối khoáng từ đất?\",Rễ cây,Cánh hoa,Quả chín,Lá non,A,\"Rễ cây bám trong đất để hút nước nuôi cả cây\",Khoa học,Lớp 2\r\n";

    private static final List<String> REQUIRED_COLUMNS = List.of("cau_hoi", "a", "b", "c", "d", "dap_an");

    public ParsedCsvResult parseAndValidate(
            String rawCsv,
            QuizSubject defaultSubject,
            QuizGrade defaultGrade,
            long existingQuestionCount
    ) {
        if (rawCsv == null || rawCsv.isBlank()) {
            throw new InvalidRequestException("Vui lòng chọn tệp CSV có dữ liệu câu hỏi.");
        }
        byte[] utf8Bytes = rawCsv.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > QuizSet.MAX_CSV_BYTES) {
            throw new InvalidRequestException("Tệp CSV vượt quá giới hạn 1 MB.");
        }

        String content = rawCsv.startsWith(UTF8_BOM) ? rawCsv.substring(1) : rawCsv;
        List<RawCsvRecord> records = parseRecords(content);
        int remainingCapacity = (int) Math.max(0L, QuizSet.MAX_QUESTIONS_PER_SET - Math.max(0L, existingQuestionCount));

        if (records.isEmpty()) {
            QuizCsvPreviewResponse empty = new QuizCsvPreviewResponse(
                    0, 0, 0, existingQuestionCount, remainingCapacity, 0,
                    "Tệp CSV đang trống, chưa có dòng tiêu đề.", List.of()
            );
            return new ParsedCsvResult(empty, List.of());
        }

        RawCsvRecord headerRecord = records.getFirst();
        if (headerRecord.unclosedQuote()) {
            QuizCsvPreviewResponse err = new QuizCsvPreviewResponse(
                    0, 0, 0, existingQuestionCount, remainingCapacity, 0,
                    "Dòng tiêu đề có dấu ngoặc kép chưa đóng.", List.of()
            );
            return new ParsedCsvResult(err, List.of());
        }

        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < headerRecord.fields().size(); i++) {
            String key = normalizeHeader(headerRecord.fields().get(i));
            if (!key.isEmpty()) {
                headerIndex.putIfAbsent(key, i);
            }
        }

        List<String> missingColumns = REQUIRED_COLUMNS.stream()
                .filter(col -> !headerIndex.containsKey(col))
                .toList();
        if (!missingColumns.isEmpty()) {
            String headerErr = "Thiếu cột bắt buộc: " + String.join(", ", missingColumns)
                    + ". Hãy tải tệp mẫu để dùng đúng tên cột.";
            QuizCsvPreviewResponse err = new QuizCsvPreviewResponse(
                    0, 0, 0, existingQuestionCount, remainingCapacity, 0, headerErr, List.of()
            );
            return new ParsedCsvResult(err, List.of());
        }

        QuizSubject fallbackSubject = defaultSubject == null ? QuizSubject.KHAC : defaultSubject;
        QuizGrade fallbackGrade = defaultGrade == null ? QuizGrade.LOP_1 : defaultGrade;

        List<QuizCsvRowPreview> previews = new ArrayList<>();
        List<ParsedValidRow> validRows = new ArrayList<>();
        int invalidCount = 0;

        for (int idx = 1; idx < records.size(); idx++) {
            RawCsvRecord record = records.get(idx);
            if (isBlankRecord(record)) {
                continue;
            }
            List<String> errors = new ArrayList<>();
            if (record.unclosedQuote()) {
                errors.add("Dòng này có dấu ngoặc kép chưa đóng.");
            }

            String questionText = readField(record, headerIndex, "cau_hoi");
            String optA = readField(record, headerIndex, "a");
            String optB = readField(record, headerIndex, "b");
            String optC = readField(record, headerIndex, "c");
            String optD = readField(record, headerIndex, "d");
            String rawAnswer = readField(record, headerIndex, "dap_an");
            String explanation = readField(record, headerIndex, "giai_thich");
            String rawSubject = readField(record, headerIndex, "mon");
            String rawGrade = readField(record, headerIndex, "lop");

            if (questionText.isEmpty()) {
                errors.add("Thiếu nội dung câu hỏi.");
            } else if (questionText.length() > 400) {
                errors.add("Câu hỏi quá dài (tối đa 400 ký tự).");
            }

            validateOptionField("A", optA, errors);
            validateOptionField("B", optB, errors);
            validateOptionField("C", optC, errors);
            validateOptionField("D", optD, errors);

            if (!optA.isEmpty() && !optB.isEmpty() && !optC.isEmpty() && !optD.isEmpty()) {
                Set<String> seen = new HashSet<>();
                for (String value : List.of(optA, optB, optC, optD)) {
                    if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                        errors.add("4 đáp án A, B, C, D không được trùng nhau.");
                        break;
                    }
                }
            }

            Optional<QuizOptionKey> parsedAnswer = QuizOptionKey.parseFlexible(rawAnswer);
            if (parsedAnswer.isEmpty()) {
                errors.add("Đáp án đúng phải là A, B, C hoặc D.");
            }

            if (explanation.length() > 400) {
                errors.add("Giải thích quá dài (tối đa 400 ký tự).");
            }

            QuizSubject resolvedSubject = fallbackSubject;
            if (!rawSubject.isEmpty()) {
                Optional<QuizSubject> parsedSubject = QuizSubject.parseFlexible(rawSubject);
                if (parsedSubject.isEmpty()) {
                    errors.add("Môn học chưa hợp lệ (chọn: Toán, Tiếng Việt, Tiếng Anh, Khoa học, Đố vui, Khác).");
                } else {
                    resolvedSubject = parsedSubject.get();
                }
            }

            QuizGrade resolvedGrade = fallbackGrade;
            if (!rawGrade.isEmpty()) {
                Optional<QuizGrade> parsedGrade = QuizGrade.parseFlexible(rawGrade);
                if (parsedGrade.isEmpty()) {
                    errors.add("Khối lớp chưa hợp lệ (chọn: Mẫu giáo, Lớp 1 đến Lớp 5).");
                } else {
                    resolvedGrade = parsedGrade.get();
                }
            }

            if (errors.isEmpty() && validRows.size() >= remainingCapacity) {
                errors.add("Bộ câu hỏi đã đủ tối đa 500 câu, dòng này sẽ không được nhập.");
            }

            boolean valid = errors.isEmpty();
            if (valid) {
                validRows.add(new ParsedValidRow(
                        record.rowNumber(),
                        questionText,
                        optA,
                        optB,
                        optC,
                        optD,
                        parsedAnswer.get(),
                        explanation.isEmpty() ? null : explanation,
                        resolvedSubject,
                        resolvedGrade
                ));
            } else {
                invalidCount++;
            }

            previews.add(new QuizCsvRowPreview(
                    record.rowNumber(),
                    valid,
                    List.copyOf(errors),
                    questionText,
                    optA,
                    optB,
                    optC,
                    optD,
                    parsedAnswer.map(Enum::name).orElse(rawAnswer),
                    explanation.isEmpty() ? null : explanation,
                    resolvedSubject.name(),
                    resolvedSubject.label(),
                    resolvedGrade.name(),
                    resolvedGrade.label()
            ));
        }

        QuizCsvPreviewResponse preview = new QuizCsvPreviewResponse(
                previews.size(),
                validRows.size(),
                invalidCount,
                existingQuestionCount,
                remainingCapacity,
                validRows.size(),
                null,
                List.copyOf(previews)
        );
        return new ParsedCsvResult(preview, List.copyOf(validRows));
    }

    private static void validateOptionField(String label, String value, List<String> errors) {
        if (value.isEmpty()) {
            errors.add("Thiếu đáp án " + label + ".");
        } else if (value.length() > 200) {
            errors.add("Đáp án " + label + " quá dài (tối đa 200 ký tự).");
        }
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.startsWith(UTF8_BOM) ? raw.substring(1) : raw;
        return cleaned.trim().toLowerCase(Locale.ROOT);
    }

    private static String readField(RawCsvRecord record, Map<String, Integer> headerIndex, String key) {
        Integer index = headerIndex.get(key);
        if (index == null || index < 0 || index >= record.fields().size()) {
            return "";
        }
        String value = record.fields().get(index);
        return value == null ? "" : value.trim();
    }

    private static boolean isBlankRecord(RawCsvRecord record) {
        return !record.unclosedQuote() && record.fields().stream().allMatch(cell -> cell == null || cell.trim().isEmpty());
    }

    // parser chuẩn RFC-4180 xử lý đủ ngoặc kép, dấu phẩy trong ô, thoát "" và giữ đúng số thứ tự dòng như trong Excel
    private static List<RawCsvRecord> parseRecords(String content) {
        List<RawCsvRecord> records = new ArrayList<>();
        List<String> currentFields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int currentRowNumber = 1;
        int recordStartRow = 1;
        int length = content.length();

        for (int i = 0; i < length; i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (ch == '\r') {
                        if (i + 1 < length && content.charAt(i + 1) == '\n') {
                            i++;
                        }
                        currentRowNumber++;
                        field.append('\n');
                    } else if (ch == '\n') {
                        currentRowNumber++;
                        field.append('\n');
                    } else {
                        field.append(ch);
                    }
                }
            } else {
                if (ch == '"' && field.isEmpty()) {
                    inQuotes = true;
                } else if (ch == ',') {
                    currentFields.add(field.toString());
                    field.setLength(0);
                } else if (ch == '\r' || ch == '\n') {
                    if (ch == '\r' && i + 1 < length && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentFields.add(field.toString());
                    field.setLength(0);
                    records.add(new RawCsvRecord(recordStartRow, List.copyOf(currentFields), false));
                    currentFields.clear();
                    currentRowNumber++;
                    recordStartRow = currentRowNumber;
                } else {
                    field.append(ch);
                }
            }
        }

        if (inQuotes || !field.isEmpty() || !currentFields.isEmpty()) {
            currentFields.add(field.toString());
            records.add(new RawCsvRecord(recordStartRow, List.copyOf(currentFields), inQuotes));
        }
        return records;
    }

    private record RawCsvRecord(int rowNumber, List<String> fields, boolean unclosedQuote) {
    }

    public record ParsedValidRow(
            int rowNumber,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            QuizOptionKey correctOption,
            String explanation,
            QuizSubject subject,
            QuizGrade grade
    ) {
    }

    public record ParsedCsvResult(QuizCsvPreviewResponse preview, List<ParsedValidRow> validRows) {
    }
}
