package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public enum IndexSyncOutcome {
    READY, PENDING, BUILDING, BLOCKED, SYNC_FAILED;

    public static IndexSyncOutcome fromAcknowledgment(JsonNode ack, long generation) {
        if (ack == null) {
            return SYNC_FAILED;
        }
        String status = ack.path("status").asText("");
        JsonNode acknowledged = ack.path("generation");
        if ("SKIPPED".equals(status) || !acknowledged.isNumber() || !Double.isFinite(acknowledged.doubleValue())
                || acknowledged.decimalValue().compareTo(BigDecimal.valueOf(generation)) != 0) {
            return SYNC_FAILED;
        }
        if ("CACHE_REQUIRED".equals(status)) {
            return BLOCKED;
        }
        if (CatalogJson.truthy(ack.path("index_ready"))) {
            return READY;
        }
        if (!CatalogJson.truthy(ack.path("status"))) {
            return PENDING;
        }
        try {
            return valueOf(status);
        } catch (IllegalArgumentException exception) {
            return SYNC_FAILED;
        }
    }
}
