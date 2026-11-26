package com.dippie.dto.profile;

// payload phụ huynh cộng thêm phút xem đột xuất trong ngày (kèm mã PIN xác nhận)
public record ParentTimeGrantRequest(String pin, Integer minutes) {
}
