package com.dippie.movie;

import com.dippie.catalog.query.CatalogReadModel;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

// Quản lý bảng ánh xạ thể loại song ngữ (mã gốc, tên tiếng Anh, nhãn tiếng Việt) và đếm số phim đang mở theo từng thể loại.
@Repository
class MovieGenreDirectory {

    private static final String ANIMATION_ID = "animation";
    private static final String ANIMATION_LABEL = "Hoạt hình";

    private final EntityManager entities;
    private volatile CachedNames cachedNames;

    MovieGenreDirectory(EntityManager entities) {
        this.entities = entities;
    }

    // Mở rộng một từ khóa thể loại sang mọi tên đồng nghĩa tiếng Anh/mã gốc để câu lọc SQL bắt đủ cả dữ liệu cũ lẫn mới.
    Function<String, Set<String>> filterNames(CatalogReadModel model) {
        return namesFor(model)::alternatives;
    }

    // Đếm các thể loại đang có phim hiển thị, gom cả bảng movie_genres lẫn cột CSV cũ về cùng mã chuẩn rồi gắn nhãn tiếng Việt.
    List<MovieGenreView> visibleGenres(CatalogReadModel model) {
        Names names = namesFor(model);
        Set<String> hiddenIds = model.hiddenMovieIds();
        // Lấy toàn bộ phim không bị ẩn kèm liên kết thể loại (nếu có) và chuỗi thể loại cũ
        String hql = """
                select movie.id, link.genreName, movie.genres
                from Movie movie left join MovieGenre link on link.movieId = movie.id
                """;
        var query = hiddenIds.isEmpty() ? entities.createQuery(hql, Object[].class)
                : entities.createQuery(hql + "where movie.id not in :hidden", Object[].class)
                        .setParameter("hidden", hiddenIds);
        // Mỗi phim chỉ tính một lần cho mỗi mã thể loại chuẩn để tránh đếm trùng khi một phim có nhiều dòng join
        Map<String, Set<String>> idsByMovie = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            Set<String> ids = idsByMovie.computeIfAbsent((String) row[0], id -> new HashSet<>());
            if (row[1] instanceof String name) {
                ids.add(names.canonical(name));
            } else if (row[2] instanceof String csv) {
                Arrays.stream(csv.split(",")).filter(name -> !name.isBlank())
                        .map(names::canonical).forEach(ids::add);
            }
        }
        // Sắp theo mã bằng TreeMap rồi gắn nhãn tiếng Việt trước khi trả về
        Map<String, Long> counts = new TreeMap<>();
        idsByMovie.values().forEach(ids -> ids.forEach(id -> counts.merge(id, 1L, Long::sum)));
        return counts.entrySet().stream()
                .map(entry -> new MovieGenreView(entry.getKey(), names.label(entry.getKey()), entry.getValue()))
                .toList();
    }

    // So sánh tham chiếu trực tiếp vì CatalogReadModel là snapshot bất biến; chỉ dựng lại bảng tên khi cache danh mục đổi bản mới.
    private Names namesFor(CatalogReadModel model) {
        CachedNames current = cachedNames;
        if (current != null && current.model() == model) {
            return current.names();
        }
        Names built = buildNames(model.genres());
        cachedNames = new CachedNames(model, built);
        return built;
    }

    private static Names buildNames(List<CatalogReadModel.GenreEntry> genres) {
        Map<String, String> labels = new HashMap<>();
        Map<String, String> englishById = new HashMap<>();
        Map<String, String> canonicalByEnglish = new HashMap<>();
        Map<String, Set<String>> idsByEnglish = new HashMap<>();
        genres.stream()
                .sorted(Comparator.comparing(entry -> normalize(entry.id())))
                .forEach(genre -> {
                    String id = normalize(genre.id());
                    String translation = genre.enLabel();
                    if (translation != null && !translation.isBlank()) {
                        String englishName = normalize(translation);
                        englishById.put(id, englishName);
                        canonicalByEnglish.putIfAbsent(englishName, id);
                        idsByEnglish.computeIfAbsent(englishName, key -> new LinkedHashSet<>()).add(id);
                    }
                    String label = genre.viLabel();
                    labels.put(id, label == null || label.isBlank() ? id : label);
                });
        labels.putIfAbsent(ANIMATION_ID, ANIMATION_LABEL);
        return new Names(labels, englishById, canonicalByEnglish, idsByEnglish);
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    // Bộ đệm gắn bảng ánh xạ thể loại với đúng phiên bản CatalogReadModel đang dùng.
    private record CachedNames(CatalogReadModel model, Names names) {
    }

    // Bảng tra cứu hai chiều giữa mã thể loại chuẩn, tên tiếng Anh và nhãn hiển thị tiếng Việt.
    private record Names(
            Map<String, String> labels,
            Map<String, String> englishById,
            Map<String, String> canonicalByEnglish,
            Map<String, Set<String>> idsByEnglish
    ) {

        // Gom cả mã gốc lẫn các tên tiếng Anh tương đương cho cùng một token lọc.
        Set<String> alternatives(String token) {
            Set<String> matches = new LinkedHashSet<>();
            matches.add(token);
            String english = englishById.get(token);
            if (english != null) {
                matches.add(english);
            }
            Set<String> ids = idsByEnglish.get(token);
            if (ids != null) {
                matches.addAll(ids);
            }
            return matches;
        }

        // Đưa tên thể loại bất kỳ (mã gốc hoặc tiếng Anh) về đúng mã chuẩn để đếm không bị tách làm hai dòng.
        String canonical(String rawName) {
            String token = normalize(rawName);
            if (labels.containsKey(token)) {
                return token;
            }
            return canonicalByEnglish.getOrDefault(token, token);
        }

        String label(String id) {
            return labels.getOrDefault(id, id);
        }
    }
}
