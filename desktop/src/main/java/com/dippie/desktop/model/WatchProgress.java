package com.dippie.desktop.model;

// Một dòng tiến độ xem của người dùng trên một tập phim.
public record WatchProgress(long id, long userId, String episodeId, long positionMs) {
}
