package com.dippie.controller.report;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.report.CreatePlaybackReportRequest;
import com.dippie.dto.report.PlaybackReportCreatedResponse;
import com.dippie.security.ClientIpResolver;
import com.dippie.service.report.PlaybackReportService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint công khai cho cả khách lẫn thành viên gửi báo lỗi khi phát video trục trặc
@RestController
@RequestMapping("/api/reports")
public class PlaybackReportController {

    private final PlaybackReportService reportService;
    private final ClientIpResolver clientIpResolver;

    public PlaybackReportController(PlaybackReportService reportService, ClientIpResolver clientIpResolver) {
        this.reportService = reportService;
        this.clientIpResolver = clientIpResolver;
    }

    // lấy IP thật của client để áp rate limit, lưu xong trả 201 kèm id báo cáo
    @PostMapping("/playback")
    public ResponseEntity<ApiResponse<PlaybackReportCreatedResponse>> submitPlaybackReport(
            @RequestBody(required = false) CreatePlaybackReportRequest request,
            HttpServletRequest servletRequest
    ) {
        PlaybackReportCreatedResponse created = reportService.submit(request, clientIpResolver.resolve(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Cảm ơn bạn đã báo lỗi. Dippie đã ghi nhận và sẽ sớm kiểm tra."));
    }
}
