package com.dippie.desktop;

import com.dippie.desktop.dao.*;
import com.dippie.desktop.model.DashboardCounts;
import com.dippie.desktop.model.TopRated;
import com.dippie.desktop.model.catalog.EpisodeNode;
import com.dippie.desktop.model.catalog.SeasonNode;
import com.dippie.desktop.service.CatalogService;
import com.dippie.desktop.service.DashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// test lắp cây danh mục Composite, lọc theo từ khóa và đọc số liệu tổng quan trực tiếp từ H2
class ReadServiceTest extends TestDatabase {
    @Test
    void dashboardCountsAndRanksLiveRows() {
        var dashboard = new DashboardService(db, new SeriesDao(db), new EpisodeDao(db),
                new UserDao(db), new RatingDao(db), new WatchProgressDao(db)).load();

        assertEquals(new DashboardCounts(2, 3, 3, 5, 4), dashboard.counts());
        assertEquals(List.of("e2", "e1"), dashboard.topRated().stream().map(TopRated::episodeId).toList());
        assertEquals(8.5, dashboard.topRated().get(1).average());
        assertEquals(2, dashboard.topRated().get(1).count());
        assertEquals(1L, dashboard.ratingCounts().get(10));
        assertEquals("Bộ Một", dashboard.mostWatched().getFirst().title());
    }

    @Test
    void catalogBuildsTheCompositeWithLiveRatingsAndBothSources() {
        var tree = service().load();
        var series = tree.getFirst();
        assertEquals("s1", series.id());
        assertEquals(7.0, series.averageRating());
        assertEquals(List.of("m1", "m2"), series.children().stream().map(SeasonNode::id).toList());
        EpisodeNode episode = series.children().getFirst().children().getFirst();
        assertEquals(125000L, episode.durationMs());
        assertEquals(8.5, episode.averageRating());
        assertEquals(List.of("https://www.youtube.com/watch?v=lmnopqrstuv",
                "https://drive.google.com/file/d/abc_123/view"), episode.sources());
        var unassigned = tree.get(1).children().getFirst();
        assertNull(unassigned.id());
        assertEquals("e3", unassigned.children().getFirst().id());
        assertEquals(List.of("https://www.youtube.com/watch?v=ABCDEFGHIJK"),
                unassigned.children().getFirst().sources());
    }

    @Test
    void catalogFilterRetainsAncestorsAndExpandsMatchingBranches() {
        var tree = service().load();
        var matchingEpisode = CatalogService.filter(tree, "  BI MAT  ");
        assertEquals(1, matchingEpisode.size());
        assertEquals("m1", matchingEpisode.getFirst().children().getFirst().id());
        assertEquals("e1", matchingEpisode.getFirst().children().getFirst().children().getFirst().id());
        assertEquals(2, CatalogService.filter(tree, "First Series").getFirst().children().size());
        assertEquals(1, CatalogService.filter(tree, "Mùa sau").getFirst().children().size());
        assertTrue(CatalogService.filter(tree, "không có").isEmpty());
        assertEquals(2, tree.getFirst().children().size());
    }

    private CatalogService service() {
        return new CatalogService(db, new SeriesDao(db), new SeasonDao(db), new EpisodeDao(db),
                new MediaAssetDao(db), new EpisodeAssetDao(db), new RatingDao(db));
    }
}
