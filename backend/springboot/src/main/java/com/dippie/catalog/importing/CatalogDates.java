package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Tiện ích đọc ngày phát hành và mốc thời gian ISO 8601 khi nhập danh mục.
final class CatalogDates {

    private static final String DATE_FORMAT = "[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])";
    private static final Pattern DATE = Pattern.compile(DATE_FORMAT);
    private static final Pattern DATE_TIME = Pattern.compile("(" + DATE_FORMAT
            + "[T ](?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:[.][0-9]{1,6})?)"
            + "(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])?");

    private CatalogDates() {
    }

    // Lấy giờ hiện tại theo Clock rồi cắt về độ chính xác micro giây cho khớp cột DATETIME của MySQL.
    static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    // Đọc release_date theo chuẩn YYYY-MM-DD, riêng tập phim có thể lấy dự phòng từ air_date.
    static LocalDate releaseDate(JsonNode item, boolean episode) {
        JsonNode release = item.path("release_date");
        JsonNode dateNode = CatalogJson.or(release, episode ? item.path("air_date") : MissingNode.getInstance());
        if (CatalogJson.absent(dateNode)) {
            return null;
        }
        String text = dateNode.textValue();
        if (text == null || !DATE.matcher(text).matches()) {
            throw new IllegalArgumentException("Ngày phải đúng định dạng YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("day is out of range for month");
        }
    }

    // Chuyển chuỗi ISO 8601 về múi giờ UTC rồi chặn các năm nằm ngoài giới hạn DATETIME.
    static LocalDateTime retrievedAt(JsonNode timestampNode) {
        if (CatalogJson.absent(timestampNode)) {
            return null;
        }
        Matcher match = DATE_TIME.matcher(timestampNode.asText());
        if (!timestampNode.isTextual() || !match.matches()) {
            throw new IllegalArgumentException("Thời gian phải đúng định dạng ISO 8601.");
        }
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(match.group(1).replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("day is out of range for month");
        }
        // Phải cộng trừ độ lệch múi giờ về UTC trước khi kiểm tra năm, tránh trường hợp quy đổi xong mới vượt biên.
        String offset = match.group(2);
        if (offset != null && !offset.equals("Z")) {
            int minutes = Integer.parseInt(offset.substring(1, 3)) * 60 + Integer.parseInt(offset.substring(4));
            time = time.plusMinutes(offset.startsWith("-") ? minutes : -minutes);
        }
        if (time.getYear() > 9999) {
            throw new IllegalArgumentException("date value out of range");
        }
        if (time.getYear() < 1000) {
            throw new IllegalArgumentException("Thời gian UTC nằm ngoài giới hạn DATETIME.");
        }
        return time;
    }
}
