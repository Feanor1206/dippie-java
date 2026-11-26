package com.dippie.model.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Xác minh quy tắc đọc trạng thái xuất bản từ recordJson, ép về DRAFT khi JSON sai hoặc không thỏa luật ready.
class PublicationStatusTest {

    @Test
    void recognizesPublishedAndArchivedRecordsCaseInsensitively() {
        assertThat(PublicationStatus.of("{\"status\":\"ReAdY\"}")).isEqualTo(PublicationStatus.READY);
        assertThat(PublicationStatus.of("{\"status\":\"ARCHIVED\"}")).isEqualTo(PublicationStatus.ARCHIVED);
        assertThat(PublicationStatus.READY.isPublished()).isTrue();
        assertThat(PublicationStatus.DRAFT.isPublished()).isFalse();
        assertThat(PublicationStatus.ARCHIVED.isPublished()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "[]", "true", "{}", "{", "{\"status\":null}",
            "{\"status\":1}", "{\"status\":\"draft\"}", "{\"status\":\" ready \"}",
            "{\"status\":\"ready\"} {}", "{\"status\":\"re\\u0061dy\"}", "{\"status\":\"ready\",\"status\":\"draft\"}"})
    void defaultsToDraftWhenTheRecordDoesNotMeetThePublicationRule(String recordJson) {
        assertThat(PublicationStatus.of(recordJson)).isEqualTo(PublicationStatus.DRAFT);
    }

    // Trường hợp status bị escape unicode chỉ được nhận READY nếu bản thân raw JSON vẫn có chữ "ready".
    @Test
    void acceptsEscapedReadyWhenTheRawRecordAlsoContainsReady() {
        assertThat(PublicationStatus.of("{\"status\":\"re\\u0061dy\",\"title\":\"Ready\"}"))
                .isEqualTo(PublicationStatus.READY);
    }
}
