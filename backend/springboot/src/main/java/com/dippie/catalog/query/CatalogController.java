package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogSeriesView;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

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

    @GetMapping("/series/{seriesId}")
    public CatalogQueryService.SeriesDetail seriesDetail(@PathVariable String seriesId) {
        return catalog.seriesDetail(seriesId, isAdmin(currentViewer.find()));
    }

    @GetMapping("/episodes/{episodeId}")
    public CatalogQueryService.EpisodeDetail episodeDetail(@PathVariable String episodeId) {
        Optional<Viewer> viewer = currentViewer.find();
        return catalog.episodeDetail(episodeId, viewer.isPresent(), isAdmin(viewer));
    }

    @GetMapping("/search")
    public List<CatalogSearchService.Hit> search(@RequestParam(defaultValue = "") String q) {
        Optional<Viewer> viewer = currentViewer.find();
        return search.search(q, viewer.isPresent(), isAdmin(viewer));
    }

    private static boolean isAdmin(Optional<Viewer> viewer) {
        return viewer.map(Viewer::isAdmin).orElse(false);
    }
}
