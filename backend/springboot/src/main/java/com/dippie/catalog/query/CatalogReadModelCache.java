package com.dippie.catalog.query;

import com.dippie.catalog.importing.CatalogImportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class CatalogReadModelCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReadModelCache.class);

    private final AtomicReference<CatalogReadModel> model = new AtomicReference<>();
    private final CatalogReadModelLoader loader;

    CatalogReadModelCache(CatalogReadModelLoader loader) {
        this.loader = loader;
    }

    public CatalogReadModel current() {
        CatalogReadModel current = model.get();
        return current == null ? initialize() : current;
    }

    public synchronized void invalidate() {
        model.set(null);
    }

    @EventListener
    public synchronized void refresh(CatalogImportedEvent event) {
        try {
            model.set(loader.load());
        } catch (RuntimeException exception) {
            invalidate();
            LOGGER.error("Could not refresh catalog read model for snapshot {}", event.snapshotId(), exception);
        }
    }

    private synchronized CatalogReadModel initialize() {
        CatalogReadModel current = model.get();
        if (current == null) {
            current = loader.load();
            model.set(current);
        }
        return current;
    }
}
