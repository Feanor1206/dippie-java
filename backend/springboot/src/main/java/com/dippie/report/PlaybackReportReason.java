package com.dippie.report;

import com.dippie.common.error.InvalidRequestException;

// Năm mã lý do báo lỗi hợp lệ mà API chấp nhận, khớp với ràng buộc CHECK dưới bảng playback_reports.
public enum PlaybackReportReason {

    KHONG_PHAT("khong_phat"),
    SAI_TAP("sai_tap"),
    AM_THANH("am_thanh"),
    NOI_DUNG_KHONG_PHU_HOP("noi_dung_khong_phu_hop"),
    KHAC("khac");

    private static final String INVALID_REASON_MESSAGE = "Vui lòng chọn một lý do báo lỗi hợp lệ.";

    private final String code;

    PlaybackReportReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    // Đọc mã lý do từ JSON sang enum tương ứng, sai hoặc bỏ trống thì ném lỗi 400.
    public static PlaybackReportReason parse(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new InvalidRequestException(INVALID_REASON_MESSAGE);
        }
        String normalized = rawReason.trim();
        for (PlaybackReportReason candidate : values()) {
            if (candidate.code.equals(normalized)) {
                return candidate;
            }
        }
        throw new InvalidRequestException(INVALID_REASON_MESSAGE);
    }
}
