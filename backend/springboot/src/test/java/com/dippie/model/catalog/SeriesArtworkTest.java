package com.dippie.model.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// test thứ tự chọn poster với backdrop phim bộ, lọc URL ngoài http/https rồi check fillMissing bù ảnh thiếu
class SeriesArtworkTest {

    private static final SeriesArtwork YOUTUBE = new SeriesArtwork(
            "https://i.ytimg.com/vi/video_00001/hqdefault.jpg",
            "https://i.ytimg.com/vi/video_00001/maxresdefault.jpg");

    @Test
    void prefersPosterAndBackdropOverOtherArtworkAndYoutube() {
        assertThat(SeriesArtwork.fromSeries("""
                [{"role":"thumbnail","uri":"https://example.com/thumb.jpg"},
                 {"role":"banner","uri":"https://example.com/banner.jpg"},
                 {"role":"poster","uri":"http://example.com/poster.jpg"},
                 {"role":"backdrop","uri":"https://example.com/backdrop.jpg"}]
                """, YOUTUBE)).isEqualTo(new SeriesArtwork(
                        "http://example.com/poster.jpg", "https://example.com/backdrop.jpg"));
    }

    // bỏ qua mấy URL nguy hiểm hay thiếu host kiểu javascript: để nó rớt xuống thumbnail với banner
    @Test
    void acceptsBannerAndThumbnailWhenPreferredRolesHaveInvalidUris() {
        assertThat(SeriesArtwork.fromSeries("""
                [null,{"role":"poster","uri":"javascript:alert(1)"},
                 {"role":"poster","uri":"//example.com/poster.jpg"},
                 {"role":"backdrop","uri":"https:missing-host"},
                 {"role":"thumbnail","uri":"https://example.com/thumb.jpg"},
                 {"role":"banner","uri":"https://example.com/banner.jpg"}]
                """, YOUTUBE)).isEqualTo(new SeriesArtwork(
                        "https://example.com/thumb.jpg", "https://example.com/banner.jpg"));
    }

    @Test
    void usesYoutubeThenPosterWhenBackdropIsMissing() {
        String poster = "https://example.com/poster.jpg";
        String artwork = """
                [{"role":"poster","uri":"https://example.com/poster.jpg"},
                 {"role":"backdrop","uri":"file:///backdrop.jpg"}]
                """;
        assertThat(SeriesArtwork.fromSeries(artwork, YOUTUBE)).isEqualTo(new SeriesArtwork(poster, YOUTUBE.backdropUrl()));
        assertThat(SeriesArtwork.fromSeries(artwork, SeriesArtwork.EMPTY)).isEqualTo(new SeriesArtwork(poster, poster));
        assertThat(SeriesArtwork.fromSeries("malformed", YOUTUBE)).isEqualTo(YOUTUBE);
        assertThat(SeriesArtwork.fromSeries(null, SeriesArtwork.EMPTY)).isEqualTo(SeriesArtwork.EMPTY);
    }

    @Test
    void fillsEachMissingArtFieldIndependently() {
        String existing = "https://example.com/existing.jpg";
        assertThat(YOUTUBE.fillMissing(existing, null)).isEqualTo(new SeriesArtwork(existing, YOUTUBE.backdropUrl()));
        assertThat(YOUTUBE.fillMissing(null, existing)).isEqualTo(new SeriesArtwork(YOUTUBE.posterUrl(), existing));
    }
}
