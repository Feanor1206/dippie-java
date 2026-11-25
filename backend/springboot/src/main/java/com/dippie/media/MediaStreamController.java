package com.dippie.media;

import com.dippie.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Controller REST phục vụ phát video (HEAD và GET hỗ trợ HTTP Range) cùng API lấy mô tả media của phim.
@RestController
public class MediaStreamController {

    private static final Logger log = LoggerFactory.getLogger(MediaStreamController.class);
    private static final String BYTES_UNIT = "bytes";

    private final MediaStreamService mediaStreamService;

    public MediaStreamController(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    // Trình duyệt thường gọi HEAD trước để biết dung lượng tổng và xác nhận server hỗ trợ tua bằng byte range.
    @RequestMapping(value = "/api/media/stream/{assetId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headMedia(
            @PathVariable String assetId,
            @RequestParam(required = false) String grant,
            @RequestParam(required = false) String token
    ) {
        ResolvedMediaStream stream = mediaStreamService.resolveStream(assetId, grant, token);
        return ResponseEntity.ok()
                .contentType(stream.mediaType())
                .header(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT)
                .contentLength(stream.totalLength())
                .build();
    }

    // Ghi đồng bộ thẳng ra HttpServletResponse thay vì trả StreamingResponseBody để tránh bị Spring MVC cắt vì async timeout khi xem video dài.
    @GetMapping("/api/media/stream/{assetId}")
    public void streamMedia(
            @PathVariable String assetId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(required = false) String grant,
            @RequestParam(required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        // Kiểm tra quyền và tách header Range trước khi gửi header, nếu Range sai thì trả 416 kèm Content-Range.
        ResolvedMediaStream stream = mediaStreamService.resolveStream(assetId, grant, token);
        ByteRange range;
        try {
            range = ByteRange.parse(rangeHeader, stream.totalLength());
        } catch (RangeNotSatisfiableException ex) {
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, ex.contentRangeHeader());
            return;
        }

        // Gửi 200 cho toàn bộ tệp hoặc 206 cho đoạn byte được yêu cầu.
        response.setContentType(stream.mediaType().toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT);
        long start;
        long length;
        if (!range.partial()) {
            response.setStatus(HttpStatus.OK.value());
            start = 0L;
            length = stream.totalLength();
        } else {
            response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, range.contentRangeHeader());
            start = range.start();
            length = range.length();
        }
        response.setContentLengthLong(length);

        try {
            stream.writeRange(start, length, response.getOutputStream());
        } catch (IOException ex) {
            // Người xem tua video hoặc đóng tab giữa chừng sẽ ngắt socket sau khi response đã commit, bỏ qua để log khỏi nổ lỗi giả.
            if (response.isCommitted()) {
                log.debug("Client ngắt luồng phát sớm cho video {}", assetId);
                return;
            }
            throw ex;
        }
    }

    @GetMapping("/api/movies/{movieId}/media-descriptor")
    public ApiResponse<MediaDescriptorView> getMediaDescriptor(@PathVariable String movieId) {
        return ApiResponse.ok(mediaStreamService.getMediaDescriptor(movieId));
    }
}
