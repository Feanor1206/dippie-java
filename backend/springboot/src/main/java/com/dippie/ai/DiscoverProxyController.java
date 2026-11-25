package com.dippie.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscoverProxyController {

    private final AiServiceClient aiServiceClient;

    public DiscoverProxyController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/v1/discover")
    public ResponseEntity<byte[]> discover(@RequestBody JsonNode request) {
        return toResponseEntity(aiServiceClient.forwardDiscover(request));
    }

    @PostMapping("/v1/search")
    public ResponseEntity<byte[]> search(@RequestBody JsonNode request) {
        return toResponseEntity(aiServiceClient.forwardSearch(request));
    }

    private ResponseEntity<byte[]> toResponseEntity(RawJsonResponse upstream) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, upstream.contentType());
        upstream.firstHeader(HttpHeaders.RETRY_AFTER)
                .ifPresent(value -> headers.set(HttpHeaders.RETRY_AFTER, value));
        return ResponseEntity.status(upstream.statusCode())
                .headers(headers)
                .body(upstream.body());
    }
}
