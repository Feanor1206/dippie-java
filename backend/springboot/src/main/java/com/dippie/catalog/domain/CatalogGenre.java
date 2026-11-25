package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

// Thể loại phim trong catalog (kế thừa CatalogRecord), tự parse nhãn đa ngôn ngữ từ labelsJson vì dữ liệu nguồn có thể ở dạng object hoặc mảng.
@Entity
@Table(name = "catalog_genres")
public class CatalogGenre extends CatalogRecord {

    @Column(name = "labels_json", columnDefinition = "LONGTEXT", nullable = false)
    private String labelsJson;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    protected CatalogGenre() {
    }

    // Chép đè nhãn, tên gọi khác và mô tả thể loại từ bản ghi mới import.
    public void applyImport(CatalogGenre imported) {
        replaceRecordFrom(imported);
        labelsJson = imported.labelsJson;
        aliasesJson = imported.aliasesJson;
        description = imported.description;
    }

    public CatalogGenre(String id, String labelsJson, String aliasesJson, String description, String recordJson,
            String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.labelsJson = labelsJson;
        this.aliasesJson = aliasesJson;
        this.description = description;
    }

    // Trích nhãn của thể loại theo mã ngôn ngữ truyền vào, trả null nếu JSON lỗi hoặc không có ngôn ngữ đó.
    public String label(String language) {
        return JsonText.parse(labelsJson).map(labels -> extractLabel(labels, language)).orElse(null);
    }

    // Chọn nhãn hiển thị ưu tiên tiếng Việt, rồi tiếng Anh, nhãn hợp lệ đầu tiên trong JSON, cuối cùng mới lấy id.
    public String preferredLabel() {
        return JsonText.parse(labelsJson).map(labels -> {
            String vi = extractLabel(labels, "vi");
            if (vi != null) {
                return vi;
            }
            String en = extractLabel(labels, "en");
            return en != null ? en : labelValues(labels).findFirst().orElse(getId());
        }).orElse(getId());
    }

    // Gom id, mọi nhãn đa ngôn ngữ và danh sách aliases thành tập từ khóa tìm kiếm bất biến.
    public Set<String> allSearchTerms() {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(getId());
        JsonText.parse(labelsJson).ifPresent(labels -> labelValues(labels).forEach(terms::add));
        // Mỗi phần tử trong aliasesJson có thể là chuỗi thẳng hoặc object có trường text.
        JsonText.parse(aliasesJson).filter(JsonNode::isArray).ifPresent(aliases -> aliases.valueStream()
                .map(alias -> alias.isTextual() ? alias.textValue() : alias.path("text").textValue())
                .filter(term -> term != null && !term.isBlank()).map(String::strip).forEach(terms::add));
        return Set.copyOf(terms);
    }

    // Hỗ trợ cả hai kiểu cấu trúc labelsJson: object { "vi": "..." } và mảng [{ "language": "vi", "text": "..." }].
    private static Stream<String> labelValues(JsonNode labels) {
        Stream<String> raw = labels.isObject() ? labels.valueStream().map(JsonNode::textValue)
                : labels.isArray() ? labels.valueStream().map(item -> item.path("text").textValue()) : Stream.empty();
        return raw.filter(text -> text != null && !text.isBlank()).map(String::strip);
    }

    // Tìm nhãn khớp đúng mã ngôn ngữ cho cả dạng object lẫn dạng mảng.
    private static String extractLabel(JsonNode labels, String language) {
        if (labels.isObject()) {
            String text = labels.path(language).textValue();
            return text == null || text.isBlank() ? null : text.strip();
        }
        return labels.valueStream().filter(label -> language.equals(label.path("language").textValue()))
                .map(label -> label.path("text").textValue()).filter(text -> text != null && !text.isBlank())
                .map(String::strip).findFirst().orElse(null);
    }

    public String getAliasesJson() {
        return aliasesJson;
    }
}
