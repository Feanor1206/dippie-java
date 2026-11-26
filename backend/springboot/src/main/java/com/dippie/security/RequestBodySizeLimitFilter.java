package com.dippie.security;

import com.dippie.common.web.ApiResponse;
import com.dippie.exception.RequestBodyTooLargeException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

// filter chặn body vượt trần theo từng route trước khi Jackson đọc vào RAM kẻo payload khổng lồ gây OOM
@Component
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final Set<String> DETAIL_ERROR_PATHS = Set.of("/v1/search", "/v1/discover");
    private static final Set<String> CATALOG_IMPORT_PATHS = Set.of(
            "/api/admin/catalog/import",
            "/api/admin/catalog/validate"
    );

    private final HttpBodyLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(HttpBodyLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // chặn sớm nếu Content-Length đã lố trần, đồng thời bọc InputStream để đếm byte thật cả với request chunked
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = normalizePath(request);
        long limit = resolveLimit(path);

        // check Content-Length trước để cắt sớm khỏi mất công đọc body
        long contentLength = Math.max(request.getContentLengthLong(), parseContentLengthHeader(request));
        if (contentLength > limit) {
            writeTooLargeResponse(path, response);
            return;
        }

        // bọc request bằng stream đếm byte để chặn cả Transfer-Encoding: chunked không báo độ dài
        BoundedBodyRequestWrapper wrapped = new BoundedBodyRequestWrapper(request, limit);
        try {
            filterChain.doFilter(wrapped, response);
        } catch (Throwable ex) {
            if (containsTooLargeException(ex) && !response.isCommitted()) {
                writeTooLargeResponse(path, response);
                return;
            }
            if (ex instanceof ServletException servletException) {
                throw servletException;
            }
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ServletException(ex);
        }
    }

    private long resolveLimit(String path) {
        if (CATALOG_IMPORT_PATHS.contains(path)) {
            return properties.catalogImportMaxBytes();
        }
        if ("/api/chat".equals(path)) {
            return properties.chatMaxBytes();
        }
        return properties.defaultMaxBytes();
    }

    // trả 413 JSON đúng format từng nhóm route, /v1 dùng field detail còn lại dùng ApiResponse
    private void writeTooLargeResponse(String path, HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        Object body = DETAIL_ERROR_PATHS.contains(path)
                ? Map.of("detail", RequestBodyTooLargeException.DEFAULT_MESSAGE)
                : ApiResponse.failure(RequestBodyTooLargeException.DEFAULT_MESSAGE);
        try (PrintWriter writer = response.getWriter()) {
            objectMapper.writeValue(writer, body);
        }
        response.flushBuffer();
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private static long parseContentLengthHeader(HttpServletRequest request) {
        String header = request.getHeader("Content-Length");
        if (header == null || header.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    // dò ngược chuỗi cause vì Spring MVC hay bọc RequestBodyTooLargeException trong HttpMessageNotReadableException
    public static boolean containsTooLargeException(Throwable throwable) {
        Throwable cursor = throwable;
        int depth = 0;
        while (cursor != null && depth < 16) {
            if (cursor instanceof RequestBodyTooLargeException) {
                return true;
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }

    // wrapper giữ chung 1 luồng đọc có đếm byte cho cả getInputStream và getReader
    private static final class BoundedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream boundedStream;
        private BufferedReader reader;

        BoundedBodyRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        @SuppressWarnings("resource") // stream gốc là của servlet container, Tomcat tự đóng sau request
        public ServletInputStream getInputStream() throws IOException {
            if (boundedStream == null) {
                boundedStream = new BoundedServletInputStream(super.getInputStream(), maxBytes);
            }
            return boundedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    // stream đếm số byte thật đi qua, vượt giới hạn là ném RequestBodyTooLargeException liền
    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int nextByte = delegate.read();
            if (nextByte != -1) {
                addBytes(1);
            }
            return nextByte;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                addBytes(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void addBytes(int bytesAdded) {
            bytesRead += bytesAdded;
            if (bytesRead > maxBytes) {
                throw new RequestBodyTooLargeException();
            }
        }
    }
}
