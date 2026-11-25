package com.dippie.catalog.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dippie.catalog.domain.CatalogSnapshot;
import com.dippie.catalog.domain.CatalogSnapshotRepository;
import com.dippie.catalog.domain.CatalogSnapshotView;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Kiểm tra vòng đời snapshot: list() luôn chỉ đọc, bản mới nhất cập nhật theo bộ tìm kiếm, bản cũ bị bỏ qua và CACHE_REQUIRED cũ chuyển sang BLOCKED.
class CatalogSnapshotReconciliationTest {

    private final CatalogSnapshotRepository repository = mock(CatalogSnapshotRepository.class);
    private final CatalogExportService export = mock(CatalogExportService.class);
    private final CatalogIndexPublisher publisher = mock(CatalogIndexPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T04:00:00Z"), ZoneOffset.UTC);
    private CatalogSnapshotService service;

    @BeforeEach
    void setUp() {
        when(export.exportForIndex()).thenReturn(JsonNodeFactory.instance.objectNode());
        service = new CatalogSnapshotService(repository, export, publisher, clock);
    }

    @Test
    void listSnapshotsIsStrictlyReadOnlyWithoutMutatingOrPublishing() {
        CatalogSnapshot pending = snapshot("snap_g2", 2, "BUILDING");
        when(repository.findAllByOrderBySnapshotVersionDesc()).thenReturn(List.of(pending));

        List<CatalogSnapshotView> views = service.list();

        assertThat(views).hasSize(1);
        assertThat(pending.getStatus()).isEqualTo("BUILDING");
        verify(publisher, never()).publish(anyLong(), anyString(), any(), anyBoolean());
    }

    @Test
    void latestBuildingSnapshotBecomesReadyOnceTheAiIndexAcceptsIt() {
        CatalogSnapshot latest = snapshot("snap_g2", 2, "BUILDING");
        when(repository.findAllByOrderBySnapshotVersionDesc()).thenReturn(List.of(latest));
        when(publisher.publish(2L, "snap_g2", export.exportForIndex(), false)).thenReturn(IndexSyncOutcome.READY);

        service.reconcilePending();
        List<CatalogSnapshotView> views = service.list();

        assertThat(latest.getStatus()).isEqualTo("READY");
        assertThat(latest.getCompletedAt()).isNotNull();
        assertThat(views).hasSize(1);
    }

    @Test
    void latestBuildingSnapshotStaysBuildingWhileTheAiIndexIsStillPreparing() {
        CatalogSnapshot latest = snapshot("snap_g2", 2, "BUILDING");
        when(repository.findAllByOrderBySnapshotVersionDesc()).thenReturn(List.of(latest));
        when(publisher.publish(anyLong(), anyString(), any(), anyBoolean())).thenReturn(IndexSyncOutcome.BUILDING);

        service.reconcilePending();

        assertThat(latest.getStatus()).isEqualTo("BUILDING");
        assertThat(latest.getCompletedAt()).isNull();
    }

    @Test
    void supersededAwaitingSnapshotsFailWithoutContactingTheAiService() {
        CatalogSnapshot latest = snapshot("snap_g3", 3, "READY");
        CatalogSnapshot superseded = snapshot("snap_g2", 2, "PENDING");
        when(repository.findAllByOrderBySnapshotVersionDesc()).thenReturn(List.of(latest, superseded));

        service.reconcilePending();

        assertThat(latest.getStatus()).isEqualTo("READY");
        assertThat(superseded.getStatus()).isEqualTo("SYNC_FAILED");
        verify(publisher, never()).publish(anyLong(), anyString(), any(), anyBoolean());
    }

    @Test
    void legacyCacheRequiredStatusIsReportedAsBlocked() {
        CatalogSnapshot latest = snapshot("snap_g1", 1, "CACHE_REQUIRED");
        when(repository.findAllByOrderBySnapshotVersionDesc()).thenReturn(List.of(latest));

        service.reconcilePending();

        assertThat(latest.getStatus()).isEqualTo("BLOCKED");
        verify(publisher, never()).publish(anyLong(), anyString(), any(), anyBoolean());
    }

    private static CatalogSnapshot snapshot(String id, long version, String status) {
        return new CatalogSnapshot(id, version, "hash", 10, status, 0, null, null);
    }
}
