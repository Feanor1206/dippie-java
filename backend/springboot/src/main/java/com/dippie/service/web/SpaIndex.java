package com.dippie.service.web;

import com.dippie.config.web.WebProperties;
import com.dippie.exception.ServiceUnavailableException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Đọc file index.html đã build của frontend và xử lý fallback cho các đường dẫn trình duyệt.
@Component
public class SpaIndex {

    private static final List<String> RESERVED_PATHS =
            List.of("/api", "/v1", "/v3", "/assets", "/illustrations", "/swagger-ui");

    private final WebProperties properties;

    public SpaIndex(WebProperties properties) {
        this.properties = properties;
    }

    // Phục vụ index.html với mã 200; nếu chưa build frontend thì báo 503 kèm lệnh build.
    public ResponseEntity<Resource> index() {
        return serve(HttpStatus.OK).orElseThrow(() -> new ServiceUnavailableException(
                "Chưa build giao diện. Chạy npm run build trong thư mục frontend."));
    }

    // Route lạ mà trình duyệt gọi GET/HEAD kèm Accept: text/html thì vẫn trả index.html nhưng giữ mã 404, tránh chặn nhầm API hay file tĩnh.
    public Optional<ResponseEntity<Resource>> fallback(HttpServletRequest request) {
        String method = request.getMethod();
        String path = pathWithinApplication(request);
        if (isReservedPath(path) || !("GET".equals(method) || "HEAD".equals(method)) || !acceptsHtml(request)) {
            return Optional.empty();
        }
        return serve(HttpStatus.NOT_FOUND);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean isReservedPath(String path) {
        for (String prefix : RESERVED_PATHS) {
            if (path.startsWith(prefix) && (path.length() == prefix.length() || path.charAt(prefix.length()) == '/')) {
                return true;
            }
        }
        return false;
    }

    private static boolean acceptsHtml(HttpServletRequest request) {
        try {
            var headers = Collections.list(request.getHeaders(HttpHeaders.ACCEPT));
            return MediaType.parseMediaTypes(headers).stream().anyMatch(type -> type.getQualityValue() > 0
                    && MediaType.TEXT_HTML.equalsTypeAndSubtype(type));
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private Optional<ResponseEntity<Resource>> serve(HttpStatus status) {
        var indexPath = properties.indexHtml();
        if (!Files.isRegularFile(indexPath)) {
            return Optional.empty();
        }
        return Optional.of(ResponseEntity.status(status).contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache").varyBy(HttpHeaders.ACCEPT)
                .body(new FileSystemResource(indexPath)));
    }
}
