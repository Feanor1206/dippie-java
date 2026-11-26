package com.dippie.model.progress;

import com.dippie.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;

// command kiểm tra đầu vào cho lệnh lưu tiến độ, quy ước CREATE_VERSION = -1 cho lần lưu đầu
// parse thẳng JsonNode thay vì DTO thường để chặn Jackson tự ép boolean, số thực hay chuỗi số sang long
public record WatchProgressCommand(String assetId, long expectedVersion, long positionMs) {

    public static final long CREATE_VERSION = -1L;
    public static final long MAX_POSITION_MS = 86_400_000L;

    // đọc rồi check chặt assetId, expectedVersion (nguyên >= -1) với positionMs (0 tới 24 tiếng)
    public static WatchProgressCommand from(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidRequestException("Thông tin tiến độ xem chưa hợp lệ.");
        }
        // check mã video với version khóa lạc quan
        String assetId = extractAssetId(body.get("assetId"));
        JsonNode versionNode = body.get("expectedVersion");
        if (assetId.isEmpty() || versionNode == null || versionNode.isNull() || versionNode.isBoolean()) {
            throw new InvalidRequestException("Thiếu mã video hoặc phiên bản tiến độ xem.");
        }
        if (!versionNode.isIntegralNumber() || !versionNode.canConvertToLong()) {
            throw new InvalidRequestException("Phiên bản tiến độ xem phải là số nguyên.");
        }
        long expectedVersion = versionNode.longValue();
        if (expectedVersion < CREATE_VERSION) {
            throw new InvalidRequestException("Phiên bản cần là -1 khi lưu lần đầu hoặc khớp phiên bản hiện tại khi cập nhật.");
        }

        // chặn số âm, kiểu không phải số nguyên với mốc giờ quá 24 tiếng
        JsonNode positionNode = body.get("positionMs");
        if (positionNode == null
                || positionNode.isNull()
                || positionNode.isBoolean()
                || !positionNode.isIntegralNumber()
                || !positionNode.canConvertToLong()) {
            throw new InvalidRequestException("Thời điểm xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây.");
        }
        long positionMs = positionNode.longValue();
        if (positionMs < 0 || positionMs > MAX_POSITION_MS) {
            throw new InvalidRequestException("Thời điểm xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây.");
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
