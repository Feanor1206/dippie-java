package com.dippie.controller.report;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.report.PlaybackReportView;
import com.dippie.dto.report.UpdatePlaybackReportStatusRequest;
import com.dippie.service.report.PlaybackReportService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// API quản trị cho admin lọc danh sách báo lỗi phát video và đánh dấu trạng thái xử lý.
@RestController
@RequestMapping("/api/admin/reports")
public class AdminPlaybackReportController {

    private final PlaybackReportService reportService;

    public AdminPlaybackReportController(PlaybackReportService reportService) {
        this.reportService = reportService;
    }

    // Không truyền status hoặc truyền all thì trả toàn bộ, tối đa 200 báo cáo mới nhất.
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaybackReportView>>> listReports(
            @RequestParam(name = "status", required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.listForAdmin(status)));
    }

    // Chuyển trạng thái một báo cáo sang resolved hoặc dismissed kèm ghi nhật ký.
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaybackReportView>> updateReportStatus(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UpdatePlaybackReportStatusRequest request
    ) {
        PlaybackReportView updated = reportService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Đã cập nhật trạng thái báo cáo."));
    }
}
