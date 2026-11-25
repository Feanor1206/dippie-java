package com.dippie.progress;

import com.dippie.common.error.InvalidRequestException;
import com.fasterxml.jackson.databind.JsonNode;

public record WatchProgressCommand(String assetId, long expectedVersion, long positionMs) {

    public static final long CREATE_VERSION = -1L;
    public static final long MAX_POSITION_MS = 86_400_000L;

    public static WatchProgressCommand from(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidRequestException("Dữ liệu tiến độ xem không hợp lệ.");
        }
        String assetId = extractAssetId(body.get("assetId"));
        JsonNode versionNode = body.get("expectedVersion");
        if (assetId.isEmpty() || versionNode == null || versionNode.isNull() || versionNode.isBoolean()) {
            throw new InvalidRequestException("Cần mã video và phiên bản tiến độ xem.");
        }
        if (!versionNode.isIntegralNumber() || !versionNode.canConvertToLong()) {
            throw new InvalidRequestException("Phiên bản tiến độ xem phải là số nguyên.");
        }
        long expectedVersion = versionNode.longValue();
        if (expectedVersion < CREATE_VERSION) {
            throw new InvalidRequestException("Dùng phiên bản -1 khi tạo mới hoặc phiên bản hiện tại khi cập nhật.");
        }

        JsonNode positionNode = body.get("positionMs");
        if (positionNode == null
                || positionNode.isNull()
                || positionNode.isBoolean()
                || !positionNode.isIntegralNumber()
                || !positionNode.canConvertToLong()) {
            throw new InvalidRequestException("Vị trí xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây.");
        }
        long positionMs = positionNode.longValue();
        if (positionMs < 0 || positionMs > MAX_POSITION_MS) {
            throw new InvalidRequestException("Vị trí xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây.");
        }
        return new WatchProgressCommand(assetId, expectedVersion, positionMs);
    }

    public boolean isCreate() {
        return expectedVersion == CREATE_VERSION;
    }

    private static String extractAssetId(JsonNode node) {
        if (node == null || node.isNull() || (node.isBoolean() && !node.booleanValue())) {
            return "";
        }
        return node.asText("").strip();
    }
}
