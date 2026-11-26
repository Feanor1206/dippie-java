package com.dippie.dto.vip;

import java.util.List;

// payload phụ huynh cập nhật danh sách phim bộ VIP cho 1 hồ sơ trẻ em kèm mã PIN
public record UpdateVipSeriesRequest(String pin, List<String> seriesIds) {
}
