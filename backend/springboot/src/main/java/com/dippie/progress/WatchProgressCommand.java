package com.dippie.progress;

import com.dippie.common.error.InvalidRequestException;
import com.fasterxml.jackson.databind.JsonNode;

// Command gom dữ liệu và kiểm tra đầu vào cho lệnh lưu tiến độ xem.
// Mình parse thẳng JsonNode thay vì map sang DTO thường để chặn trường hợp Jackson tự ép boolean, số thập phân hay chuỗi số thành long; quy ước CREATE_VERSION = -1 cho lần lưu đầu.
public record WatchProgressCommand(String assetId, long expectedVersion, long positionMs) {

    public static final long CREATE_VERSION = -1L;
    public static final long MAX_POSITION_MS = 86_400_000L;

    // Đọc và kiểm tra chặt assetId, expectedVersion (>= -1, kiểu nguyên) và positionMs (0 đến 24 giờ).
    public static WatchProgressCommand from(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidRequestException("Thông tin tiến độ xem chưa hợp lệ.");
        }
        // Kiểm tra mã video và phiên bản khóa lạc quan gửi kèm
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

        // Chặn số âm, kiểu không phải số nguyên và thời điểm vượt quá giới hạn 24 giờ
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
