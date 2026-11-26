package com.dippie.service.quiz;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizSet;
import com.dippie.model.quiz.QuizSubject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test đọc và kiểm tra CSV câu hỏi: BOM Excel, dấu phẩy trong ngoặc kép, lỗi từng dòng tiếng Việt, giới hạn 1 MB và trần 500 câu
class QuizCsvParserTest {

    private final QuizCsvParser parser = new QuizCsvParser();

    @Test
    void parsesDownloadableTemplateWithUtf8BomAndThreeValidRows() {
        assertThat(QuizCsvParser.TEMPLATE_CSV).startsWith(QuizCsvParser.UTF8_BOM);

        QuizCsvParser.ParsedCsvResult result = parser.parseAndValidate(
                QuizCsvParser.TEMPLATE_CSV,
                QuizSubject.KHAC,
                QuizGrade.LOP_1,
                0L
        );

        assertThat(result.preview().headerError()).isNull();
        assertThat(result.preview().totalRows()).isEqualTo(3);
        assertThat(result.preview().validCount()).isEqualTo(3);
        assertThat(result.preview().invalidCount()).isZero();
        assertThat(result.validRows()).hasSize(3);
        assertThat(result.validRows().get(0).subject()).isEqualTo(QuizSubject.TOAN);
        assertThat(result.validRows().get(0).grade()).isEqualTo(QuizGrade.LOP_3);
        assertThat(result.validRows().get(1).subject()).isEqualTo(QuizSubject.TIENG_ANH);
        assertThat(result.validRows().get(1).grade()).isEqualTo(QuizGrade.MAU_GIAO);
        assertThat(result.validRows().get(2).questionText()).contains("nước, muối khoáng");
    }

    @Test
    void handlesBomQuotedCommasEscapedQuotesAndSeparatesBadRowsWithVietnameseErrors() {
        String csv = QuizCsvParser.UTF8_BOM
                + "cau_hoi,a,b,c,d,dap_an,giai_thich,mon,lop\r\n"
                + "\"Từ \"\"apple\"\", quả táo có màu gì?\",\"Đỏ, tươi\",Xanh dương,Tím,Đen,a,\"Táo chín hay có màu đỏ, giòn ngọt\",Tiếng Anh,Lớp 1\r\n"
                + ",1,2,3,4,A,,Toán,Lớp 1\r\n"
                + "Câu trùng đáp án,10,10,20,30,B,,Toán,Lớp 2\r\n"
                + "Câu sai cột đáp án,1,2,3,4,E,,Toán,Lớp 2\r\n"
                + "Câu sai môn và lớp,1,2,3,4,C,,Vũ trụ học,Lớp 9\r\n"
                + "Câu hợp lệ dùng môn lớp mặc định,Mèo,Chó,Vịt,Gà,A,,,\r\n";

        QuizCsvParser.ParsedCsvResult result = parser.parseAndValidate(
                csv,
                QuizSubject.DO_VUI,
                QuizGrade.LOP_2,
                0L
        );

        assertThat(result.preview().headerError()).isNull();
        assertThat(result.preview().totalRows()).isEqualTo(6);
        assertThat(result.preview().validCount()).isEqualTo(2);
        assertThat(result.preview().invalidCount()).isEqualTo(4);
        assertThat(result.validRows()).hasSize(2);

        QuizCsvParser.ParsedValidRow first = result.validRows().get(0);
        assertThat(first.questionText()).isEqualTo("Từ \"apple\", quả táo có màu gì?");
        assertThat(first.optionA()).isEqualTo("Đỏ, tươi");
        assertThat(first.correctOption()).isEqualTo(QuizOptionKey.A);
        assertThat(first.explanation()).isEqualTo("Táo chín hay có màu đỏ, giòn ngọt");

        QuizCsvParser.ParsedValidRow second = result.validRows().get(1);
        assertThat(second.subject()).isEqualTo(QuizSubject.DO_VUI);
        assertThat(second.grade()).isEqualTo(QuizGrade.LOP_2);

        assertThat(result.preview().rows().get(1).valid()).isFalse();
        assertThat(result.preview().rows().get(1).errors()).anyMatch(msg -> msg.contains("Thiếu nội dung câu hỏi"));
        assertThat(result.preview().rows().get(2).errors()).anyMatch(msg -> msg.contains("không được trùng nhau"));
        assertThat(result.preview().rows().get(3).errors()).anyMatch(msg -> msg.contains("A, B, C hoặc D"));
        assertThat(result.preview().rows().get(4).errors())
                .anyMatch(msg -> msg.contains("Môn học chưa hợp lệ"))
                .anyMatch(msg -> msg.contains("Khối lớp chưa hợp lệ"));
    }

    @Test
    void reportsMissingRequiredHeadersClearly() {
        String csv = "cau_hoi,a,b,c\n1+1=?,1,2,3\n";
        QuizCsvParser.ParsedCsvResult result = parser.parseAndValidate(csv, QuizSubject.TOAN, QuizGrade.LOP_1, 0L);

        assertThat(result.preview().headerError()).contains("Thiếu cột bắt buộc").contains("d").contains("dap_an");
        assertThat(result.validRows()).isEmpty();
    }

    @Test
    void rejectsFilesExceedingOneMegabyteLimit() {
        StringBuilder oversized = new StringBuilder("cau_hoi,a,b,c,d,dap_an\n");
        String chunk = "Câu hỏi rất dài để vượt giới hạn 1 megabyte,1,2,3,4,A\n";
        while (oversized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= QuizSet.MAX_CSV_BYTES) {
            oversized.append(chunk);
        }

        assertThatThrownBy(() -> parser.parseAndValidate(oversized.toString(), QuizSubject.TOAN, QuizGrade.LOP_1, 0L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("1 MB");
    }

    @Test
    void enforcesFiveHundredQuestionCapacityPerSet() {
        String csv = "cau_hoi,a,b,c,d,dap_an\n"
                + "Câu 1,1,2,3,4,A\n"
                + "Câu 2,1,2,3,4,B\n"
                + "Câu 3,1,2,3,4,C\n"
                + "Câu 4,1,2,3,4,D\n";

        QuizCsvParser.ParsedCsvResult result = parser.parseAndValidate(csv, QuizSubject.TOAN, QuizGrade.LOP_1, 498L);

        assertThat(result.preview().remainingCapacity()).isEqualTo(2);
        assertThat(result.preview().validCount()).isEqualTo(2);
        assertThat(result.preview().invalidCount()).isEqualTo(2);
        assertThat(result.preview().willImportCount()).isEqualTo(2);
        assertThat(result.validRows()).hasSize(2);
        assertThat(result.preview().rows().get(2).errors()).anyMatch(msg -> msg.contains("500"));
    }
}
