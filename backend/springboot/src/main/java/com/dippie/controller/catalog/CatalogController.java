package com.dippie.controller.catalog;

import com.dippie.dto.catalog.CatalogEpisodeDetail;
import com.dippie.dto.catalog.CatalogSearchHit;
import com.dippie.dto.catalog.CatalogSeriesDetail;
import com.dippie.dto.catalog.CatalogSeriesView;
import com.dippie.dto.catalog.SeriesCard;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogQueryService;
import com.dippie.service.catalog.CatalogSearchService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

// Controller REST cho nhóm API /api/catalog (danh sách bộ phim, chi tiết bộ/tập và tìm kiếm nhanh).
// Mọi endpoint đều lấy quyền người xem từ CurrentViewer rồi chuyển xuống tầng service lọc dữ liệu.
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CurrentViewer currentViewer;
    private final CatalogQueryService catalog;
    private final CatalogSearchService search;

    public CatalogController(CurrentViewer currentViewer, CatalogQueryService catalog, CatalogSearchService search) {
        this.currentViewer = currentViewer;
        this.catalog = catalog;
        this.search = search;
    }

    @GetMapping("/series")
    public List<CatalogSeriesView> series() {
        return catalog.listSeries(isAdmin(currentViewer.find()));
    }

    @GetMapping("/series/cards")
    public List<SeriesCard> seriesCards() {
        return catalog.listSeriesCards();
    }

    // Lấy chi tiết một bộ phim kèm các mùa và lát cắt tập phim theo offset/limit tùy chọn.
    @GetMapping("/series/{seriesId}")
    public CatalogSeriesDetail seriesDetail(
            @PathVariable String seriesId,
            @RequestParam(required = false) Integer episodeOffset,
            @RequestParam(required = false) Integer episodeLimit
    ) {
        return catalog.seriesDetail(seriesId, isAdmin(currentViewer.find()), episodeOffset, episodeLimit);
    }

    // Lấy chi tiết tập phim kèm phân đoạn, tệp video khả dụng, phân cảnh và nguồn phát YouTube.
    @GetMapping("/episodes/{episodeId}")
    public CatalogEpisodeDetail episodeDetail(@PathVariable String episodeId) {
        Optional<Viewer> viewer = currentViewer.find();
        return catalog.episodeDetail(episodeId, viewer.isPresent(), isAdmin(viewer));
    }

    // Tìm nhanh trong danh mục theo phạm vi hiển thị của khách, thành viên hoặc quản trị viên.
    @GetMapping("/search")
    public List<CatalogSearchHit> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer limit
    ) {
        Optional<Viewer> viewer = currentViewer.find();
        return search.search(q, viewer.isPresent(), isAdmin(viewer), limit);
    }

    private static boolean isAdmin(Optional<Viewer> viewer) {
        return viewer.map(Viewer::isAdmin).orElse(false);
    }
}
