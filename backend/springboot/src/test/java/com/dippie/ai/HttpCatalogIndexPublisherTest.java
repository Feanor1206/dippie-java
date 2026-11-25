package com.dippie.ai;

import com.dippie.catalog.importing.IndexSyncOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpCatalogIndexPublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final HttpCatalogIndexPublisher publisher = new HttpCatalogIndexPublisher(client, mapper);

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"generation":7,"status":"SKIPPED","index_ready":true} | SYNC_FAILED
            {"generation":6,"status":"CACHE_REQUIRED","index_ready":true} | SYNC_FAILED
            {"generation":7,"status":"CACHE_REQUIRED","index_ready":true} | BLOCKED
            {"generation":7,"status":"BUILDING","index_ready":true} | READY
            {"generation":7,"status":"BUILDING","index_ready":false} | BUILDING
            {"generation":7,"status":"SYNC_FAILED","index_ready":false} | SYNC_FAILED
            {"generation":7,"status":null} | PENDING
            """)
    void sendsSyncRequestAndAppliesAcknowledgmentPrecedence(String ack, IndexSyncOutcome expected) throws Exception {
        JsonNode catalog = mapper.readTree("{\"episodes\":[]}");
        JsonNode payload = mapper.readTree("""
                {"catalog":{"episodes":[]},"generation":7,"content_hash":null,"force_rebuild":true}
                """);
        when(client.syncIndex(any())).thenReturn(mapper.readTree(ack));

        assertThat(publisher.publish(7, "snapshot", catalog, true)).isEqualTo(expected);
        var sent = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).syncIndex(sent.capture());
        assertThat(mapper.readTree(sent.getValue().toString())).isEqualTo(payload);
    }

    @Test
    void mapsClientExceptionsToSyncFailed() {
        when(client.syncIndex(any())).thenThrow(new AiUnavailableException(new IOException()));
        assertThat(publisher.publish(7, "snapshot", mapper.createObjectNode(), false)).isEqualTo(IndexSyncOutcome.SYNC_FAILED);
    }
}
