package com.dippie.assistant.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
class LocalDiscoverController {

    private final LocalCatalogSearch catalog;
    private final ObjectMapper mapper;

    LocalDiscoverController(LocalCatalogSearch catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @PostMapping({"/v1/search", "/v1/discover"})
    DiscoverResponse search(@RequestBody(required = false) JsonNode body) {
        var request = DiscoverRequest.parse(body);
        var index = catalog.index();
        if (index == null) {
            throw new DiscoverRequest.Rejection(HttpStatus.SERVICE_UNAVAILABLE, "Chưa có snapshot catalog được chấp nhận.");
        }
        var matches = LocalCatalogSearch.matches(index, request.query(), request.limit(), request.constraints());
        return DiscoverResponse.of(request.query(), matches, mapper);
    }

    @ExceptionHandler(DiscoverRequest.Rejection.class)
    ResponseEntity<ErrorBody> rejected(DiscoverRequest.Rejection exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorBody(exception.detail()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException exception) {
        long offset = 0;
        if (exception.getCause() instanceof JsonProcessingException json && json.getLocation() != null) {
            offset = Math.max(0, json.getLocation().getByteOffset() - 1);
        }
        return ResponseEntity.unprocessableEntity().body(new ErrorBody(List.of(Map.of("type", "json_invalid",
                "loc", List.of("body", offset), "msg", "JSON decode error", "input", Map.of(),
                "ctx", Map.of("error", "Invalid JSON")))));
    }

    record ErrorBody(Object detail) {
    }
}
