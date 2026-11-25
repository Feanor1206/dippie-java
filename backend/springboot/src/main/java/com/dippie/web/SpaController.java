package com.dippie.web;

import com.dippie.common.error.ServiceUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SpaController {

    private static final String MISSING_FRONTEND_MESSAGE =
            "Chưa build giao diện. Chạy npm run build trong thư mục frontend.";

    private final WebProperties webProperties;

    public SpaController(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @RequestMapping(
            path = {"/", "/search", "/profile", "/account", "/admin/import", "/admin/users", "/admin/stats", "/admin/audit",
                    "/movies/{id}", "/series", "/series/{id}"},
            method = {RequestMethod.GET, RequestMethod.HEAD}
    )
    public ResponseEntity<Resource> index() {
        Path indexHtml = webProperties.indexHtml();
        if (!Files.isRegularFile(indexHtml)) {
            throw new ServiceUnavailableException(MISSING_FRONTEND_MESSAGE);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(new FileSystemResource(indexHtml));
    }
}
