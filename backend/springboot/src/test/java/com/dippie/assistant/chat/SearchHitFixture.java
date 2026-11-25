package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import com.dippie.common.rating.RatingSummary;
import java.util.List;
import java.util.Map;

// Builder dựng nhanh dữ liệu SearchHit mẫu cho các bài test trong gói assistant.chat.
final class SearchHitFixture {

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

    static SearchHitFixture builder() {
        return new SearchHitFixture();
    }

    SearchHitFixture id(String id) {
        this.id = id;
        return this;
    }

    SearchHitFixture title(String title) {
        this.title = title;
        return this;
    }

    SearchHitFixture runtime(String runtime) {
        this.runtime = runtime;
        return this;
    }

    SearchHitFixture genres(List<String> genres) {
        this.genres = genres;
        return this;
    }

    SearchHitFixture description(String description) {
        this.description = description;
        return this;
    }

    SearchHitFixture itemType(String itemType) {
        this.itemType = itemType;
        return this;
    }

    SearchHitFixture episodeId(String episodeId) {
        this.episodeId = episodeId;
        return this;
    }

    SearchHitFixture episodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
        return this;
    }

    SearchHitFixture startMs(Long startMs) {
        this.startMs = startMs;
        return this;
    }

    SearchHitFixture startMsSnake(Long startMsSnake) {
        this.startMsSnake = startMsSnake;
        return this;
    }

    SearchHitFixture characters(List<String> characters) {
        this.characters = characters;
        return this;
    }

    SearchHitFixture plotFacts(List<String> plotFacts) {
        this.plotFacts = plotFacts;
        return this;
    }

    SearchHitFixture keywords(List<String> keywords) {
        this.keywords = keywords;
        return this;
    }

    SearchHitFixture themes(List<String> themes) {
        this.themes = themes;
        return this;
    }

    SearchHitFixture episodeTitle(String episodeTitle) {
        this.episodeTitle = episodeTitle;
        return this;
    }

    SearchHitFixture episodeCount(Integer episodeCount) {
        this.episodeCount = episodeCount;
        return this;
    }

    SearchHitFixture seasonCount(Integer seasonCount) {
        this.seasonCount = seasonCount;
        return this;
    }

    SearchHitFixture diagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    SearchHitFixture evidence(List<SearchEvidence> evidence) {
        this.evidence = evidence;
        return this;
    }

    SearchHit build() {
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
