package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.dippie.catalog.importing.IndexSyncOutcome.READY;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogIndexPublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"generation":7,"status":"SKIPPED","index_ready":true} | SYNC_FAILED
            {"generation":6,"status":"CACHE_REQUIRED","index_ready":true} | SYNC_FAILED
            {"generation":7,"status":"CACHE_REQUIRED","index_ready":true} | BLOCKED
            {"generation":7,"status":"BUILDING","index_ready":true} | READY
            {"generation":7,"status":"BUILDING","index_ready":false} | BUILDING
            {"generation":7,"status":null} | PENDING
            """)
    void appliesPythonAcknowledgmentPrecedence(String ack, IndexSyncOutcome expected) throws Exception {
        assertThat(IndexSyncOutcome.fromAcknowledgment(mapper.readTree(ack), 7)).isEqualTo(expected);
    }

    @Test
    void aConfiguredPublisherReplacesTheFallback() {
        CatalogIndexPublisher publisher = (generation, snapshotId, catalog, forceRebuild) -> READY;

        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CatalogIndexPublisherConfiguration.class))
                .withBean(CatalogIndexPublisher.class, () -> publisher)
                .run(context -> {
                    assertThat(context).hasSingleBean(CatalogIndexPublisher.class);
                    assertThat(context.getBean(CatalogIndexPublisher.class)).isSameAs(publisher);
                });
    }
}
