package com.dippie.dto.profile;

// payload nhịp tim gửi số giây bé vừa xem kể từ lần báo trước
public record ScreenTimeHeartbeatRequest(Integer seconds) {
}
