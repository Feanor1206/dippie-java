package com.dippie.media;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra quy tắc tự gán ổ đĩa Drive mặc định theo hệ điều hành trong MediaProperties.
class MediaPropertiesTest {

    @ParameterizedTest
    @CsvSource({
            ", Windows 11, X:",
            "'', Windows Server 2025, X:",
            "'  ', Windows 10, X:",
            "'', Linux, ''",
            ", Mac OS X, ''",
            "M:, Windows 11, M:",
            "/mnt/drive, Linux, /mnt/drive"
    })
    void defaultsBlankMountUsingTheOsName(String configured, String osName, String expected) {
        assertThat(MediaProperties.defaultDriveMount(configured, osName)).isEqualTo(expected);
    }
}
