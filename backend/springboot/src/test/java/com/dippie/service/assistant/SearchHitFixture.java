package com.dippie.service.assistant;

import com.dippie.common.rating.RatingSummary;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import java.util.List;
import java.util.Map;

// Builder dựng nhanh dữ liệu SearchHit mẫu cho các bài test CineBot và tìm kiếm.
public final class SearchHitFixture {

    private String id = "hit-1";
    private String title = "Tập 1";
    private int year = 2020;
    private double rating = 8.0;
    private String runtime = "22m";
    private List<String> genres = List.of();
    private String description = "";
    private String itemType = "episode";
    private String episodeId;
    private Integer episodeNumber;
    private Long startMs;
    private Long startMsSnake;
    private List<String> characters;
    private List<String> plotFacts;
    private List<String> keywords;
    private List<String> themes;
    private String episodeTitle;
    private Map<String, Object> diagnostics;
    private Integer episodeCount;
    private Integer seasonCount;
    private List<SearchEvidence> evidence = List.of();

    public static SearchHitFixture builder() {
        return new SearchHitFixture();
    }

    public SearchHitFixture id(String id) {
        this.id = id;
        return this;
    }

    public SearchHitFixture title(String title) {
        this.title = title;
        return this;
    }

    public SearchHitFixture runtime(String runtime) {
        this.runtime = runtime;
        return this;
    }

    public SearchHitFixture genres(List<String> genres) {
        this.genres = genres;
        return this;
    }

    public SearchHitFixture description(String description) {
        this.description = description;
        return this;
    }

    public SearchHitFixture itemType(String itemType) {
        this.itemType = itemType;
        return this;
    }

    public SearchHitFixture episodeId(String episodeId) {
        this.episodeId = episodeId;
        return this;
    }

    public SearchHitFixture episodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
        return this;
    }

    public SearchHitFixture startMs(Long startMs) {
        this.startMs = startMs;
        return this;
    }

    public SearchHitFixture startMsSnake(Long startMsSnake) {
        this.startMsSnake = startMsSnake;
        return this;
    }

    public SearchHitFixture characters(List<String> characters) {
        this.characters = characters;
        return this;
    }

    public SearchHitFixture plotFacts(List<String> plotFacts) {
        this.plotFacts = plotFacts;
        return this;
    }

    public SearchHitFixture keywords(List<String> keywords) {
        this.keywords = keywords;
        return this;
    }

    public SearchHitFixture themes(List<String> themes) {
        this.themes = themes;
        return this;
    }

    public SearchHitFixture episodeTitle(String episodeTitle) {
        this.episodeTitle = episodeTitle;
        return this;
    }

    public SearchHitFixture episodeCount(Integer episodeCount) {
        this.episodeCount = episodeCount;
        return this;
    }

    public SearchHitFixture seasonCount(Integer seasonCount) {
        this.seasonCount = seasonCount;
        return this;
    }

    public SearchHitFixture diagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    public SearchHitFixture evidence(List<SearchEvidence> evidence) {
        this.evidence = evidence;
        return this;
    }

    public SearchHit build() {
        return new SearchHit(
                id,
                title,
                year,
                rating,
                runtime,
                genres,
                description,
                null,
                null,
                null,
                95,
                itemType,
                null,
                null,
                episodeId,
                null,
                episodeNumber,
                null,
                null,
                startMsSnake,
                null,
                null,
                null,
                null,
                characters,
                plotFacts,
                keywords,
                themes,
                null,
                null,
                null,
                null,
                episodeTitle,
                null,
                diagnostics,
                episodeId,
                startMs,
                1.0,
                0.9,
                evidence,
                episodeCount,
                seasonCount,
                false,
                RatingSummary.EMPTY
        );
    }
}
