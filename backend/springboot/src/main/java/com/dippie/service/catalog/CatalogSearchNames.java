package com.dippie.service.catalog;

import com.dippie.common.json.JsonText;
import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogSeries;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Gom tên gọi, tên gốc và bí danh của nhân vật hoặc bộ phim thành danh sách phẳng không trùng để đưa vào chỉ mục tìm kiếm.
public final class CatalogSearchNames {

    private final Map<String, List<String>> characters;

    public CatalogSearchNames(List<CatalogCharacter> characters) {
        this.characters = characters.stream().collect(Collectors.toMap(CatalogCharacter::getId,
                row -> of(row.getName(), null, row.getAliasesJson())));
    }

    // Đổi mảng JSON mã nhân vật sang toàn bộ tên và bí danh tương ứng.
    public List<String> characters(String idsJson) {
        return JsonText.stringList(idsJson).stream().map(id -> characters.getOrDefault(id, List.of()))
                .flatMap(List::stream).distinct().toList();
    }

    // Gộp tên phim, tên gốc, bí danh JSON và các cách gọi tắt cấu hình trong SeriesSearchAliases.
    public static List<String> series(CatalogSeries row) {
        return Stream.concat(of(row.getTitle(), row.getOriginalTitle(), row.getAliasesJson()).stream(),
                SeriesSearchAliases.forSeries(row.getId()).stream()).distinct().toList();
    }

    public static List<String> of(String title, String originalTitle, String aliasesJson) {
        return Stream.concat(Stream.of(title, originalTitle), aliases(aliasesJson).stream())
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).distinct().toList();
    }

    // Hỗ trợ cả mảng chuỗi thuần lẫn mảng object {"text": "..."} vì các gói import cũ và mới lưu hai dạng khác nhau.
    public static List<String> aliases(String aliasesJson) {
        return JsonText.parse(aliasesJson).filter(JsonNode::isArray).stream()
                .flatMap(JsonNode::valueStream)
                .map(alias -> alias.isTextual() ? alias.textValue() : alias.path("text").textValue())
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).distinct().toList();
    }
}
