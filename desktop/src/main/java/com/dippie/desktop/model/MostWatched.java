package com.dippie.desktop.model;

// 1 dòng cho bảng phim nhiều người xem nhất, Việt nhớ giữ ở model để view khỏi import WatchProgressDao
public record MostWatched(String seriesId, String title, long viewers) {
}
