package com.dippie.media;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/media/stream")
public class MediaStreamController {

    static final int CHUNK_SIZE = 64 * 1024;
    private static final String BYTES_UNIT = "bytes";

    private final MediaStreamService mediaStreamService;

    public MediaStreamController(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    @RequestMapping(value = "/{assetId}", method = RequestMethod.HEAD)
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

    @GetMapping("/{assetId}")
    public ResponseEntity<StreamingResponseBody> streamMedia(
            @PathVariable String assetId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(required = false) String grant,
            @RequestParam(required = false) String token
    ) {
        ResolvedMediaStream stream = mediaStreamService.resolveStream(assetId, grant, token);
        ByteRange range;
        try {
            range = ByteRange.parse(rangeHeader, stream.totalLength());
        } catch (RangeNotSatisfiableException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, ex.contentRangeHeader())
                    .build();
        }

        if (!range.partial()) {
            return ResponseEntity.ok()
                    .contentType(stream.mediaType())
                    .header(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT)
                    .contentLength(stream.totalLength())
                    .body(streamFile(stream.path(), 0L, stream.totalLength()));
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(stream.mediaType())
                .header(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT)
                .header(HttpHeaders.CONTENT_RANGE, range.contentRangeHeader())
                .contentLength(range.length())
                .body(streamFile(stream.path(), range.start(), range.length()));
    }

    private static StreamingResponseBody streamFile(Path path, long start, long length) {
        return outputStream -> {
            if (length <= 0) {
                return;
            }
            byte[] buffer = new byte[CHUNK_SIZE];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                channel.position(start);
                long remaining = length;
                while (remaining > 0) {
                    byteBuffer.clear();
                    byteBuffer.limit((int) Math.min(CHUNK_SIZE, remaining));
                    int bytesRead = channel.read(byteBuffer);
                    if (bytesRead <= 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }
        };
    }
}
