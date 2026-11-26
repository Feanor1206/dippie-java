package com.dippie.model.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra thứ tự ưu tiên phân giải mã YouTube (playback trước watch, rồi tới thumbnail), bỏ qua JSON lỗi và tạo ảnh bìa từ mã chỉ dùng làm thumbnail.
class YoutubeSourceTest {

    @Test
    void firstValidPlaybackSourceTakesPriorityOverWatch() {
        assertThat(YoutubeSource.from("""
                {"external_ids":[null,42,{"namespace":"youtube_playback","value":"invalid"},
                {"namespace":"youtube_watch","value":"watch_00001"},
                {"namespace":"youtube_playback","value":"play_-00001"},
                {"namespace":"youtube_playback","value":"play_-00002"}]}
                """)).isEqualTo(new YoutubeSource("play_-00001", true));
    }

    @Test
    void fallsBackToFirstValidWatchId() {
        assertThat(YoutubeSource.from("""
                {"external_ids":[{"namespace":"youtube_video_id","value":"other_00001"},
                {"namespace":"youtube_watch","value":12345678901},
                {"namespace":"youtube_watch","value":"watch_00001"},
                {"namespace":"youtube_watch","value":"watch_00002"}]}
                """)).isEqualTo(new YoutubeSource("watch_00001", false));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"[]", "{", "{\"external_ids\":{}}", "{\"external_ids\":[{\"namespace\":\"youtube_watch\",\"value\":\"invalid\"}]}"})
    void ignoresUnusableMetadata(String rawJson) {
        assertThat(YoutubeSource.from(rawJson)).isNull();
    }

    @Test
    void thumbnailOnlyVideoIdsProduceArtworkWithoutEnablingPlayback() {
        String rawJson = "{\"external_ids\":[{\"namespace\":\"youtube_video_id\",\"value\":\"2ykw6ZxTb8o\"}]}";
        assertThat(YoutubeSource.from(rawJson)).isNull();
        assertThat(YoutubeSource.artworkFrom(rawJson).posterUrl())
                .isEqualTo("https://i.ytimg.com/vi/2ykw6ZxTb8o/hqdefault.jpg");
        assertThat(YoutubeSource.artworkFrom(rawJson).backdropUrl())
                .isEqualTo("https://i.ytimg.com/vi/2ykw6ZxTb8o/maxresdefault.jpg");
    }

    @Test
    void playbackIdWinsOverThumbnailOnlyId() {
        String rawJson = "{\"external_ids\":[{\"namespace\":\"youtube_video_id\",\"value\":\"2ykw6ZxTb8o\"},"
                + "{\"namespace\":\"youtube_playback\",\"value\":\"9aFcot7_7Q4\"}]}";
        assertThat(YoutubeSource.artworkFrom(rawJson).posterUrl())
                .isEqualTo("https://i.ytimg.com/vi/9aFcot7_7Q4/hqdefault.jpg");
    }
}
