package com.dippie.desktop.model;

// Một dòng cho bảng các bộ phim nhiều người xem nhất. Việt nhớ giữ ở model để bên view chỉ nhận dữ liệu chứ không nhập từ WatchProgressDao.
public record MostWatched(String seriesId, String title, long viewers) {
}
