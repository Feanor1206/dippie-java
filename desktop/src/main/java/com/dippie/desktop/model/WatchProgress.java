package com.dippie.desktop.model;

// 1 dòng tiến độ xem của user trên 1 tập phim
public record WatchProgress(long id, long userId, String episodeId, long positionMs) {
}
