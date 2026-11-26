package com.dippie.service.user;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.model.catalog.YoutubeSource;
import com.dippie.model.library.FavoriteItem;
import com.dippie.model.library.MovieRating;
import com.dippie.model.library.WatchlistItem;
import com.dippie.model.progress.WatchProgress;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Nạp sẵn lịch sử xem, xem sau, yêu thích và đánh giá cho tài khoản demo khi bật dippie.seed-demo-data.
// Mình đặt @Order(20) để chạy sau DemoAccountSeeder (@Order(10)) và dùng mẫu Observer nghe CatalogImportedEvent.
@Component
@Order(20)
@Profile("(demo | test) & !mysql & !prod")
@ConditionalOnProperty(name = "dippie.seed-demo-data", havingValue = "true")
public class DemoHistorySeeder implements ApplicationRunner {

    private static final List<Sample> SAMPLES = List.of(
            new Sample("ep_peppa_pig_s01_0001", 1, 92_000L, false, false, 8),
            new Sample("ep_doraemon_s05_0209", 2, 360_000L, false, true, 9),
            new Sample("ep_larva_s01_0003", 4, 138_000L, false, false, 7),
            new Sample("ep_jadoo_s01_0002", 1, null, true, false, null),
            new Sample("ep_ori_s01_0004", 2, null, true, false, null),
            new Sample("ep_trang_quynh_0001", 3, null, true, false, null),
            new Sample("ep_peppa_pig_s01_0003", 3, null, false, true, null));

    private final UserRepository users;
    private final CatalogEpisodeRepository episodes;
    private final WatchProgressRepository progress;
    private final WatchlistItemRepository watchlist;
    private final FavoriteItemRepository favorites;
    private final MovieRatingRepository ratings;
    private final Clock clock;

    public DemoHistorySeeder(UserRepository users, CatalogEpisodeRepository episodes, WatchProgressRepository progress,
            WatchlistItemRepository watchlist, FavoriteItemRepository favorites, MovieRatingRepository ratings, Clock clock) {
        this.users = users;
        this.episodes = episodes;
        this.progress = progress;
        this.watchlist = watchlist;
        this.favorites = favorites;
        this.ratings = ratings;
        this.clock = clock;
    }

    // Chạy lúc khởi động nếu dữ liệu phim đã có sẵn trong DB.
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        seedHistory();
    }

    // Bắt CatalogImportedEvent để nạp bù lịch sử mẫu khi danh mục phim được import sau lúc khởi động.
    @EventListener(CatalogImportedEvent.class)
    @Transactional
    public void afterCatalogImport() {
        seedHistory();
    }

    private void seedHistory() {
        // Khóa dòng user demo bằng findByIdForUpdate để startup và catalog import chạy chồng lên nhau không chèn trùng.
        var demoUser = users.findByUsernameAndEmailAndRole(
                        DemoAccountSeeder.DEMO_USERNAME, DemoAccountSeeder.DEMO_EMAIL, Role.ROLE_USER)
                .flatMap(user -> users.findByIdForUpdate(user.getId())).filter(User::isEnabled).orElse(null);
        if (demoUser == null) {
            return;
        }
        Long userId = demoUser.getId();
        // Chỉ lấy các tập mẫu đã tồn tại và đang ở trạng thái công khai.
        var publishedEpisodes = episodes.findReadyWithParentsAndMovieByIdIn(SAMPLES.stream().map(Sample::episodeId).toList())
                .stream().filter(CatalogEpisodeRepository.EpisodeWithParents::isPublished)
                .map(CatalogEpisodeRepository.EpisodeWithParents::episode)
                .collect(Collectors.toMap(CatalogEpisode::getId, Function.identity()));
        if (publishedEpisodes.isEmpty()) {
            return;
        }
        // Đọc trước các mục demo user đã có để chạy lại seeder sẽ không ghi đè dữ liệu người test vừa sửa.
        Set<String> existingProgress = new HashSet<>(progress.findEpisodeIdsByUserId(userId));
        Set<String> existingWatchlist = new HashSet<>(watchlist.findMovieIdsByUserId(userId));
        Set<String> existingFavorites = new HashSet<>(favorites.findMovieIdsByUserId(userId));
        Set<String> existingRatings = new HashSet<>(ratings.findMovieIdsByUserId(userId));

        List<WatchProgress> newProgress = new ArrayList<>();
        List<WatchlistItem> newWatchlist = new ArrayList<>();
        List<FavoriteItem> newFavorites = new ArrayList<>();
        List<MovieRating> newRatings = new ArrayList<>();
        var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        // Dựng các bản ghi còn thiếu; tiến độ xem chỉ thêm khi tập có nguồn YouTube nhúng được và mốc thời gian hợp lệ.
        for (Sample sample : SAMPLES) {
            CatalogEpisode episode = publishedEpisodes.get(sample.episodeId());
            if (episode == null) {
                continue;
            }
            String episodeId = episode.getId();
            LocalDateTime recordedAt = now.minusDays(sample.daysAgo());
            if (sample.positionMs() != null && !existingProgress.contains(episodeId)) {
                var source = YoutubeSource.embeddableFrom(episode.getRawJson());
                var duration = EpisodeRuntime.known(episode.getDurationMs());
                if (source != null && duration != null && sample.positionMs() <= duration) {
                    newProgress.add(WatchProgress.startAt(userId, episodeId, YoutubeSource.PROGRESS_PREFIX + source.videoId(),
                            sample.positionMs(), recordedAt));
                }
            }
            if (sample.watchLater() && !existingWatchlist.contains(episodeId)) {
                newWatchlist.add(new WatchlistItem(userId, episodeId, recordedAt));
            }
            if (sample.favorite() && !existingFavorites.contains(episodeId)) {
                newFavorites.add(new FavoriteItem(userId, episodeId, recordedAt));
            }
            if (sample.rating() != null && !existingRatings.contains(episodeId)) {
                newRatings.add(new MovieRating(userId, episodeId, sample.rating(), recordedAt));
            }
        }
        if (!newProgress.isEmpty()) {
            progress.saveAll(newProgress);
        }
        if (!newWatchlist.isEmpty()) {
            watchlist.saveAll(newWatchlist);
        }
        if (!newFavorites.isEmpty()) {
            favorites.saveAll(newFavorites);
        }
        if (!newRatings.isEmpty()) {
            ratings.saveAll(newRatings);
        }
    }

    // Một dòng cấu hình cố định cho tập phim mẫu cần nạp vào tài khoản demo.
    private record Sample(String episodeId, long daysAgo, Long positionMs, boolean watchLater, boolean favorite,
            Integer rating) {
    }
}
