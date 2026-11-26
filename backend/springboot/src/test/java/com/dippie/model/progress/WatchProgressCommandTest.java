package com.dippie.model.progress;

import com.dippie.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test đơn vị cho WatchProgressCommand, chặn mấy kiểu JSON hay bị Jackson âm thầm ép kiểu như boolean, số thập phân hay chuỗi số.
class WatchProgressCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesValidCreateAndUpdateCommands() throws Exception {
        WatchProgressCommand create = WatchProgressCommand.from(
                mapper.readTree("{\"assetId\":\" ast-1 \",\"expectedVersion\":-1,\"positionMs\":0}")
        );
        assertThat(create.assetId()).isEqualTo("ast-1");
        assertThat(create.expectedVersion()).isEqualTo(-1L);
        assertThat(create.positionMs()).isEqualTo(0L);
        assertThat(create.isCreate()).isTrue();

        WatchProgressCommand update = WatchProgressCommand.from(
                mapper.readTree("{\"assetId\":\"ast-1\",\"expectedVersion\":3,\"positionMs\":86400000}")
        );
        assertThat(update.expectedVersion()).isEqualTo(3L);
        assertThat(update.positionMs()).isEqualTo(86_400_000L);
        assertThat(update.isCreate()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"text\"", "123", "true"})
    void rejectsNonObjectPayloads(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        assertThatThrownBy(() -> WatchProgressCommand.from(node))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Thông tin tiến độ xem chưa hợp lệ.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"assetId\":\"\",\"expectedVersion\":-1,\"positionMs\":0}",
            "{\"assetId\":\"   \",\"expectedVersion\":-1,\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":null,\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":true,\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":false,\"positionMs\":0}"
    })
    void rejectsMissingAssetOrMissingOrBooleanVersion(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        assertThatThrownBy(() -> WatchProgressCommand.from(node))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Thiếu mã video hoặc phiên bản tiến độ xem.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"assetId\":\"ast-1\",\"expectedVersion\":1.5,\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":1.0,\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":\"1\",\"positionMs\":0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":[],\"positionMs\":0}"
    })
    void rejectsNonIntegerExpectedVersion(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        assertThatThrownBy(() -> WatchProgressCommand.from(node))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Phiên bản tiến độ xem phải là số nguyên.");
    }

    @Test
    void rejectsExpectedVersionBelowMinusOne() throws Exception {
        JsonNode node = mapper.readTree("{\"assetId\":\"ast-1\",\"expectedVersion\":-2,\"positionMs\":0}");
        assertThatThrownBy(() -> WatchProgressCommand.from(node))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Phiên bản cần là -1 khi lưu lần đầu hoặc khớp phiên bản hiện tại khi cập nhật.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":null}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":true}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":false}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":12.5}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":12.0}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":\"100\"}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":-1}",
            "{\"assetId\":\"ast-1\",\"expectedVersion\":-1,\"positionMs\":86400001}"
    })
    void rejectsInvalidPositionValues(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        assertThatThrownBy(() -> WatchProgressCommand.from(node))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Thời điểm xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây.");
    }
}
