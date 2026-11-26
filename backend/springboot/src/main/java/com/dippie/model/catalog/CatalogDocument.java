package com.dippie.model.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

// bọc JSON đầu vào bằng defensive copy để bên ngoài có sửa bản gốc cũng không ảnh hưởng bản bên trong
public record CatalogDocument(JsonNode json) {

    public CatalogDocument {
        json = json == null ? NullNode.getInstance() : json.deepCopy();
    }

    // trả bản clone mới mỗi lần gọi để giữ bên trong bất biến
    @Override
    public JsonNode json() {
        return json.deepCopy();
    }

    // mấy service import gọi hàm này đọc thẳng cho khỏi tốn công clone, Việt đừng sửa node trả về nha
    public JsonNode readOnlyJson() {
        return json;
    }
}
