package com.dippie.controller.media;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.media.MediaDescriptorView;
import com.dippie.exception.RangeNotSatisfiableException;
import com.dippie.model.media.ByteRange;
import com.dippie.model.media.ResolvedMediaStream;
import com.dippie.service.media.MediaStreamService;

import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.io.OutputStream;

// controller phát video (HEAD với GET hỗ trợ HTTP Range) kèm API lấy mô tả media của phim
@RestController
public class MediaStreamController {

    private static final Logger log = LoggerFactory.getLogger(MediaStreamController.class);
    private static final String BYTES_UNIT = "bytes";

    private final MediaStreamService mediaStreamService;

    public MediaStreamController(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    // trình duyệt hay gọi HEAD trước để biết tổng dung lượng và xem server có cho tua bằng byte range không
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

    // ghi đồng bộ thẳng ra HttpServletResponse thay vì trả StreamingResponseBody kẻo xem phim dài bị Spring MVC cắt vì async timeout
    @GetMapping("/api/media/stream/{assetId}")
    public void streamMedia(
            @PathVariable String assetId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(required = false) String grant,
            @RequestParam(required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        // check quyền và tách header Range trước khi gửi header, Range sai thì trả 416 kèm Content-Range
        ResolvedMediaStream stream = mediaStreamService.resolveStream(assetId, grant, token);
        ByteRange range;
        try {
            range = ByteRange.parse(rangeHeader, stream.totalLength());
        } catch (RangeNotSatisfiableException ex) {
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, ex.contentRangeHeader());
            return;
        }

        // trả 200 nếu lấy cả file, còn xin đoạn byte thì trả 206
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
            try (OutputStream output = response.getOutputStream()) {
                stream.writeRange(start, length, output);
            }
        } catch (IOException ex) {
            // người xem tua hoặc đóng tab giữa chừng là đứt socket sau khi response đã commit, bỏ qua kẻo log nổ lỗi giả
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
