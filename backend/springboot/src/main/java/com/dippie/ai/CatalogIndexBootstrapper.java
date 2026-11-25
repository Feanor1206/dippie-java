package com.dippie.ai;

import com.dippie.catalog.importing.CatalogImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;

public final class CatalogIndexBootstrapper {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogIndexBootstrapper.class);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final AiServiceClient client;
    private final CatalogImportService catalog;

    public CatalogIndexBootstrapper(AiServiceClient client, CatalogImportService catalog) {
        this.client = client;
        this.catalog = catalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofVirtual().name("catalog-index-bootstrap").start(this::publishWhenReady);
    }

    private void publishWhenReady() {
        try {
            long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
            long remaining;
            while (!Thread.currentThread().isInterrupted() && (remaining = deadline - System.nanoTime()) > 0) {
                if (client.isReadyForIndexSync(Duration.ofNanos(remaining))) {
                    if (System.nanoTime() < deadline) {
                        catalog.publishCurrentIndex();
                    }
                    return;
                }
                remaining = deadline - System.nanoTime();
                if (remaining > 0) {
                    Thread.sleep(Duration.ofNanos(Math.min(remaining, POLL_INTERVAL.toNanos())));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            LOG.warn("Catalog index startup sync failed ({})", exception.getClass().getSimpleName());
        }
    }
}
