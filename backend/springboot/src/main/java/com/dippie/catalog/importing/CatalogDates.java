package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatalogDates {

    private static final String DATE_FORMAT = "[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])";
    private static final Pattern DATE = Pattern.compile(DATE_FORMAT);
    private static final Pattern DATE_TIME = Pattern.compile("(" + DATE_FORMAT
            + "[T ](?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:[.][0-9]{1,6})?)"
            + "(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])?");

    private CatalogDates() {
    }

    static LocalDate releaseDate(JsonNode item, boolean episode) {
        JsonNode release = item.path("release_date");
        JsonNode value = CatalogJson.or(release, episode ? item.path("air_date") : MissingNode.getInstance());
        if (CatalogJson.absent(value)) {
            return null;
        }
        String text = value.textValue();
        if (text == null || !DATE.matcher(text).matches()) {
            throw new IllegalArgumentException("Ngày phải có dạng YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("day is out of range for month");
        }
    }

    static LocalDateTime retrievedAt(JsonNode value) {
        if (CatalogJson.absent(value)) {
            return null;
        }
        Matcher match = DATE_TIME.matcher(value.asText());
        if (!value.isTextual() || !match.matches()) {
            throw new IllegalArgumentException("Thời điểm phải có dạng ISO 8601.");
        }
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(match.group(1).replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("day is out of range for month");
        }
        String offset = match.group(2);
        if (offset != null && !offset.equals("Z")) {
            int minutes = Integer.parseInt(offset.substring(1, 3)) * 60 + Integer.parseInt(offset.substring(4));
            time = time.plusMinutes(offset.startsWith("-") ? minutes : -minutes);
        }
        if (time.getYear() > 9999) {
            throw new IllegalArgumentException("date value out of range");
        }
        if (time.getYear() < 1000) {
            throw new IllegalArgumentException("Thời điểm UTC nằm ngoài phạm vi DATETIME.");
        }
        return time;
    }
}
