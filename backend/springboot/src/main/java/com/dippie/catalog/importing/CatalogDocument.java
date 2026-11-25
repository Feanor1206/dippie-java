package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

// Bọc cây JSON đầu vào bằng defensive copy để bên ngoài sửa bản gốc cũng không làm biến đổi dữ liệu đang xử lý.
public record CatalogDocument(JsonNode json) {

    public CatalogDocument {
        json = json == null ? NullNode.getInstance() : json.deepCopy();
    }

    // Trả về bản sao mới mỗi lần gọi từ bên ngoài để giữ trạng thái bên trong bất biến.
    @Override
    public JsonNode json() {
        return json.deepCopy();
    }

    // Các lớp tin cậy cùng package dùng hàm này để đọc thẳng, khỏi tốn công clone thêm lần nữa.
    JsonNode readOnlyJson() {
        return json;
    }
}
