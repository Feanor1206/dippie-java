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

// gom tên gọi, tên gốc với biệt danh của nhân vật hay bộ phim ra list phẳng không trùng để đẩy vào chỉ mục
public final class CatalogSearchNames {

    private final Map<String, List<String>> characters;

    public CatalogSearchNames(List<CatalogCharacter> characters) {
        this.characters = characters.stream().collect(Collectors.toMap(CatalogCharacter::getId,
                row -> of(row.getName(), null, row.getAliasesJson())));
    }

    // đổi mảng JSON mã nhân vật ra hết tên với biệt danh tương ứng
    public List<String> characters(String idsJson) {
        return JsonText.stringList(idsJson).stream().map(id -> characters.getOrDefault(id, List.of()))
                .flatMap(List::stream).distinct().toList();
    }

    // gộp tên phim, tên gốc, biệt danh JSON với mấy cách gọi tắt trong SeriesSearchAliases
    public static List<String> series(CatalogSeries row) {
        return Stream.concat(of(row.getTitle(), row.getOriginalTitle(), row.getAliasesJson()).stream(),
                SeriesSearchAliases.forSeries(row.getId()).stream()).distinct().toList();
    }

    public static List<String> of(String title, String originalTitle, String aliasesJson) {
        return Stream.concat(Stream.of(title, originalTitle), aliases(aliasesJson).stream())
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).distinct().toList();
    }

    // nhận cả mảng chuỗi lẫn mảng object {"text": chuỗi} vì gói import cũ với mới lưu 2 kiểu khác nhau
    public static List<String> aliases(String aliasesJson) {
        return JsonText.parse(aliasesJson).filter(JsonNode::isArray).stream()
                .flatMap(JsonNode::valueStream)
                .map(alias -> alias.isTextual() ? alias.textValue() : alias.path("text").textValue())
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).distinct().toList();
    }
}
