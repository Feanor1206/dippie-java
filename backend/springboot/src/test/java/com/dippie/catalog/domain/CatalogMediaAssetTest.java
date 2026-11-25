package com.dippie.catalog.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra quyền mở media asset theo trạng thái enabled, xuất bản, AccessPolicy và phạm vi phủ nguyên tập trong coverageJson.
class CatalogMediaAssetTest {

    @ParameterizedTest
    @CsvSource({
            "true, ready, public, true, true, true",
            "true, ready, authenticated, false, true, true",
            "true, ready, private, false, false, true",
            "true, ready, other, false, false, false",
            "true, draft, public, false, false, true",
            "true, archived, private, false, false, true",
            "true, draft, authenticated, false, false, true",
            "true, draft, other, false, false, false",
            "false, ready, public, false, false, false",
            "false, draft, private, false, false, false"
    })
    void requiresAnEnabledAssetAndAppliesPublicationAndAccessPolicy(
            boolean enabled, String status, String policy, boolean anonymous, boolean user, boolean admin) {
        CatalogMediaAsset asset = asset(enabled, "{\"status\":\"" + status + "\"}", policy, null);

        assertThat(asset.isAccessibleBy(false, false)).isEqualTo(anonymous);
        assertThat(asset.isAccessibleBy(true, false)).isEqualTo(user);
        assertThat(asset.isAccessibleBy(true, true)).isEqualTo(admin);
        assertThat(asset.isAccessibleBy(false, true)).isEqualTo(anonymous);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"episode_id\":\"ep-1\",\"scope\":\"full_episode\"}]",
            "[null,42,{}, {\"episode_id\":\"ep-1\",\"scope\":\"FULL_EPISODE\"}]"
    })
    void findsFullEpisodeCoverageForTheExactEpisode(String coverageJson) {
        CatalogMediaAsset asset = asset(true, null, "public", coverageJson);

        assertThat(asset.coversFullEpisode("ep-1")).isTrue();
        assertThat(asset.coversFullEpisode("EP-1")).isFalse();
        assertThat(asset.coversFullEpisode(null)).isFalse();
    }

    // Loại hết JSON hỏng, sai tên trường, khoảng trắng thừa hoặc scope chỉ phủ một đoạn.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{", "null", "{\"episode_id\":\"ep-1\",\"scope\":\"full_episode\"}",
            "[{\"episode_id\":\"ep-2\",\"scope\":\"full_episode\"}]",
            "[{\"episode_id\":\"ep-1\",\"scope\":\"segment\"}]",
            "[{\"episode_id\":\"ep-1\",\"scope\":\" full_episode \"}]",
            "[{\"episodeId\":\"ep-1\",\"scope\":\"full_episode\"}]",
            "[{\"episode_id\":\"ep-1\",\"scope\":\"full_episode\"}] garbage"})
    void rejectsInvalidAndPartialCoverage(String coverageJson) {
        assertThat(asset(true, null, "public", coverageJson).coversFullEpisode("ep-1")).isFalse();
    }

    private static CatalogMediaAsset asset(boolean enabled, String recordJson, String policy, String coverageJson) {
        return new CatalogMediaAsset("asset-1", "video", "feature", null, null, null,
                "local", "library", "episode.mp4", null, null, null, null, null,
                null, coverageJson, null, policy, enabled, recordJson, null, 1);
    }
}
