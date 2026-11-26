package com.dippie.service.catalog;

import com.dippie.model.catalog.CatalogImportedEvent;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// lúc luồng refresh đang dựng bản chụp mới thì ai gọi current() vẫn lấy bản cũ xài liền, không bị đứng chờ
class CatalogReadModelCacheTest {

    @Test
    void servesThePreviousSnapshotWhileARefreshIsBuilding() throws Exception {
        var loader = mock(CatalogReadModelLoader.class);
        var previous = mock(CatalogReadModel.class);
        var replacement = mock(CatalogReadModel.class);
        var loading = new CountDownLatch(1);
        var publish = new CountDownLatch(1);
        when(loader.load()).thenReturn(previous).thenAnswer(invocation -> {
            loading.countDown();
            assertThat(publish.await(10, TimeUnit.SECONDS)).isTrue();
            return replacement;
        });
        var cache = new CatalogReadModelCache(loader);
        assertThat(cache.current()).isSameAs(previous);

        var event = new CatalogImportedEvent("package", 2, 2, "snapshot", "admin", LocalDateTime.of(2026, 1, 1, 0, 0));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var refresh = workers.submit(() -> cache.refresh(event));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(workers.submit(cache::current).get(5, TimeUnit.SECONDS)).isSameAs(previous);
            } finally {
                publish.countDown();
            }
            refresh.get(5, TimeUnit.SECONDS);
        }
        assertThat(cache.current()).isSameAs(replacement);
        verify(loader, times(2)).load();
    }
}
