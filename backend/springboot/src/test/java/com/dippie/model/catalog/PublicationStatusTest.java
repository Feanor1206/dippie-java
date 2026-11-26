package com.dippie.model.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// test đọc trạng thái xuất bản từ recordJson, cứ lỗi JSON hay không đạt chuẩn ready là ép về DRAFT
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

    // chỗ này để ý nha, status bị escape unicode chỉ được tính READY khi raw JSON gốc vẫn có chữ "ready"
    @Test
    void acceptsEscapedReadyWhenTheRawRecordAlsoContainsReady() {
        assertThat(PublicationStatus.of("{\"status\":\"re\\u0061dy\",\"title\":\"Ready\"}"))
                .isEqualTo(PublicationStatus.READY);
    }
}
