package com.dippie.service.catalog;

import com.dippie.common.json.CatalogJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// mấy hàm tiện ích đọc ngày phát hành với mốc thời gian ISO 8601 lúc import danh mục
public final class CatalogDates {

    private static final String DATE_FORMAT = "[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])";
    private static final Pattern DATE = Pattern.compile(DATE_FORMAT);
    private static final Pattern DATE_TIME = Pattern.compile("(" + DATE_FORMAT
            + "[T ](?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:[.][0-9]{1,6})?)"
            + "(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])?");

    private CatalogDates() {
    }

    // lấy giờ hiện tại theo Clock rồi cắt về micro giây cho khớp cột DATETIME của MySQL
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    // đọc release_date chuẩn YYYY-MM-DD, riêng tập phim thiếu thì lùi về air_date
    public static LocalDate releaseDate(JsonNode item, boolean episode) {
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

    // đổi chuỗi ISO 8601 về múi giờ UTC rồi chặn mấy năm nằm ngoài biên DATETIME
    public static LocalDateTime retrievedAt(JsonNode timestampNode) {
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
        // nhớ cộng trừ độ lệch múi giờ về UTC trước khi check năm nha, kẻo quy đổi xong mới vượt biên
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
