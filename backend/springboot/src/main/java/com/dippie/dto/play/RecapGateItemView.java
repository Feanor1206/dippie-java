package com.dippie.dto.play;

// một cổng ôn tập đang khóa tập kế tiếp cho tới khi bé trả lời đúng câu hỏi của tập vừa xem
public record RecapGateItemView(String sourceEpisodeId, String targetEpisodeId) {
}
