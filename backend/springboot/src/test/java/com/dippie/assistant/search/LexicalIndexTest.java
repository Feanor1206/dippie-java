package com.dippie.assistant.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Bộ test đơn vị cho LexicalIndex: đối chiếu ưu tiên khớp chính xác so với gõ nhầm, chuẩn hóa Unicode và cách tách mốc thời gian từ bằng chứng công khai.
class LexicalIndexTest {

    @Test
    void ranksExactCharacterNamesAboveTyposAndLimitsLongTokensToTwoEdits() throws Exception {
        var payload = new ObjectMapper().readTree("""
                {"series":[{"id":"series","title":"Starlight"}],
                 "episodes":[{"id":"fuzzy","series_id":"series","title":"Adventure","character_ids":["near"]},
                             {"id":"exact","series_id":"series","title":"Puzzle","character_ids":["exact"]}],
                 "characters":[{"id":"near","name":"Nobitta"},{"id":"exact","name":"Nobita"}]}
                """);
        var index = new LexicalIndex(SearchCatalogFlattener.flatten(payload));
        assertThat(index.search("Nobita", 8, SearchConstraints.NONE)).extracting(match -> match.hit().id())
                .containsExactly("exact", "fuzzy");
        assertThat(index.search("starlxxht", 8, SearchConstraints.NONE)).isNotEmpty();
        assertThat(index.search("starxxxht", 8, SearchConstraints.NONE)).isEmpty();
    }

    @Test
    void ranksExactMatchesAboveTyposAndSupportsThreeLetterTranspositions() throws Exception {
        var mapper = new ObjectMapper();
        var typoPayload = mapper.readTree("""
                {"series":[{"id":"ser_peppa","title":"Peppa Pig"}],
                 "episodes":[{"id":"fuzzy_topic","series_id":"ser_peppa","title":"Chú khủng long xanh"}]}
                """);
        var typoIndex = new LexicalIndex(SearchCatalogFlattener.flatten(typoPayload));
        assertThat(typoIndex.search("khung log", 8, SearchConstraints.NONE)).extracting(match -> match.hit().id())
                .containsExactly("fuzzy_topic");
        assertThat(typoIndex.search("peppa pgi", 8, SearchConstraints.NONE)).extracting(match -> match.hit().id())
                .contains("ser_peppa");

        var exactPayload = mapper.readTree("""
                {"series":[{"id":"ser_peppa","title":"Peppa Pig"}],
                 "episodes":[
                   {"id":"fuzzy_topic","series_id":"ser_peppa","title":"Chú khủng long xanh"},
                   {"id":"exact_topic","series_id":"ser_peppa","title":"Nhật ký khung log mùa hè"},
                   {"id":"exact_pig","series_id":"ser_peppa","title":"Chuyện Peppa pgi nhỏ"}]}
                """);
        var exactIndex = new LexicalIndex(SearchCatalogFlattener.flatten(exactPayload));
        assertThat(exactIndex.search("khung log", 8, SearchConstraints.NONE)).extracting(match -> match.hit().id())
                .containsExactly("exact_topic");
        assertThat(exactIndex.search("peppa pgi", 8, SearchConstraints.NONE)).extracting(match -> match.hit().id())
                .startsWith("exact_pig");
    }

    @Test
    void foldsUnicodeAndUsesPythonWordCharacters() {
        assertThat(LexicalIndex.normalize("ĐỖ Straße Ａ ①")).isEqualTo("do strasse ａ ①");
        assertThat(LexicalIndex.tokenize("Đỗ, O\u0302! Straße; \"3_①\" e\u200Dt"))
                .containsExactly("đỗ", "ô", "strasse", "3_①", "e", "t");
    }

    @Test
    void keepsSourceAndEpisodeTimecodesAndIndexesOnlyPublicEvidence() throws Exception {
        var mapper = new ObjectMapper();
        var payload = mapper.readTree("""
                {
                  "series":[{"id":"series","title":"Bộ phim"}],
                  "seasons":[{"id":"season","number":1},{"id":"draft","number":2,"record":{"status":"draft"}}],
                  "episodes":[
                    {"id":"episode","series_id":"series","season_id":"season","number":1,"title":"Tập đầu"},
                    {"id":"unpublished","series_id":"series","season_id":"draft","number":1,"title":"unpublishedmarker"}
                  ],
                  "media_assets":[
                    {"id":"video","coverage":[
                      {"episode_id":"episode","source_in_ms":10000,"source_out_ms":90000,"episode_in_ms":2000}
                    ]},
                    {"id":"private","access_policy":"private"}
                  ],
                  "scenes":[
                    {"id":"scene","episode_id":"episode","media_asset_id":"video","start_ms":12000,
                     "end_ms":16000,"visual_description":"Chó giữ cửa"},
                    {"id":"hidden","episode_id":"episode","media_asset_id":"private","start_ms":12000,
                     "end_ms":16000,"visual_description":"hiddenmarker"}
                  ],
                  "text_tracks":[
                    {"id":"captions","media_asset_id":"video","cues":[
                      {"start_ms":38000,"end_ms":41000,"text":"Mật khẩu quả táo"}
                    ]},
                    {"id":"off","media_asset_id":"video","enabled":false,"cues":[
                      {"start_ms":38000,"end_ms":41000,"text":"disabledmarker"}
                    ]}
                  ]
                }
                """);
        var documents = SearchCatalogFlattener.flatten(payload);
        assertThat(documents).extracting(SearchDocument::id)
                .containsExactly("series", "episode", "scene", "dialogue:captions:episode:22500");
        var index = new LexicalIndex(documents);
        var scene = index.search("chó giữ cửa ở phút nào", 8, SearchConstraints.NONE);
        assertThat(scene).hasSize(1);
        assertThat(scene.getFirst().hit().id()).isEqualTo("episode");
        assertThat(scene.getFirst().hit().evidence()).containsExactly(
                new SearchEvidence("scene", "Chó giữ cửa", 4000L, 8000L, "scene", "file", "video", 12000L, 16000L));
        var evidence = DiscoverResponse.of("scene", scene, mapper).results().getFirst().path("evidence").get(0);
        assertThat(mapper.readTree(evidence.toString()))
                .isEqualTo(mapper.readTree("""
                        {"documentId":"scene","text":"Chó giữ cửa","startMs":4000,"endMs":8000,"itemType":"scene",
                         "sourceStartMs":12000,"sourceEndMs":16000,"mediaAssetId":"video","timeline":"episode"}
                        """));

        var dialogue = index.search("mật khẩu quả táo", 1, SearchConstraints.NONE).getFirst().hit();
        assertThat(dialogue.id()).isEqualTo("episode");
        assertThat(dialogue.startMsSnake()).isEqualTo(38000L);
        assertThat(dialogue.startMs()).isEqualTo(30000L);
        assertThat(dialogue.evidence()).containsExactly(
                new SearchEvidence("dialogue:captions:episode:22500", "Mật khẩu quả táo", 30000L, 33000L, "dialogue",
                        "file", "video", 38000L, 41000L));
        assertThat(index.search("unpublishedmarker hiddenmarker disabledmarker", 8, SearchConstraints.NONE)).isEmpty();
    }
}
