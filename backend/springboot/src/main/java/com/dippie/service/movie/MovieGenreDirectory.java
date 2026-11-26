package com.dippie.service.movie;

import com.dippie.dto.movie.MovieGenreView;
import com.dippie.service.catalog.CatalogReadModel;

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

// bảng map thể loại song ngữ (mã gốc, tên tiếng Anh, nhãn tiếng Việt) kèm đếm số phim đang mở theo thể loại
@Repository
public class MovieGenreDirectory {

    private final EntityManager entities;
    private volatile CachedNames cachedNames;

    public MovieGenreDirectory(EntityManager entities) {
        this.entities = entities;
    }

    // bung 1 từ khóa thể loại ra đủ tên tiếng Anh với mã gốc để lọc SQL bắt hết dữ liệu cũ lẫn mới
    public Function<String, Set<String>> filterNames(CatalogReadModel model) {
        return namesFor(model)::alternatives;
    }

    // đếm thể loại đang có phim mở, gom cả bảng movie_genres với cột CSV cũ về cùng mã chuẩn rồi gắn nhãn tiếng Việt
    public List<MovieGenreView> visibleGenres(CatalogReadModel model) {
        Names names = namesFor(model);
        Set<String> hiddenIds = model.hiddenMovieIds();
        // kéo hết phim không bị ẩn kèm link thể loại và chuỗi thể loại cũ
        String hql = """
                select movie.id, link.genreName, movie.genres
                from Movie movie left join MovieGenre link on link.movieId = movie.id
                """;
        var query = hiddenIds.isEmpty() ? entities.createQuery(hql, Object[].class)
                : entities.createQuery(hql + "where movie.id not in :hidden", Object[].class)
                        .setParameter("hidden", hiddenIds);
        // mỗi phim chỉ tính 1 lần cho mỗi mã chuẩn kẻo 1 phim nhiều dòng join bị đếm trùng
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
        // dùng TreeMap sắp sẵn theo mã rồi gắn nhãn tiếng Việt trước khi trả
        Map<String, Long> counts = new TreeMap<>();
        idsByMovie.values().forEach(ids -> ids.forEach(id -> counts.merge(id, 1L, Long::sum)));
        return counts.entrySet().stream()
                .map(entry -> new MovieGenreView(entry.getKey(), names.label(entry.getKey()), entry.getValue()))
                .toList();
    }

    // so thẳng tham chiếu == vì CatalogReadModel là snapshot bất biến, chỉ dựng lại bảng tên khi cache đổi bản mới
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
        labels.putIfAbsent("animation", "Hoạt hình");
        return new Names(labels, englishById, canonicalByEnglish, idsByEnglish);
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    // cache gắn bảng map thể loại theo đúng bản CatalogReadModel đang xài
    private record CachedNames(CatalogReadModel model, Names names) {
    }

    // bảng tra 2 chiều giữa mã chuẩn, tên tiếng Anh với nhãn tiếng Việt
    private record Names(
            Map<String, String> labels,
            Map<String, String> englishById,
            Map<String, String> canonicalByEnglish,
            Map<String, Set<String>> idsByEnglish
    ) {

        // gom cả mã gốc lẫn mấy tên tiếng Anh tương đương cho cùng 1 token lọc
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

        // kéo tên thể loại bất kỳ (mã gốc hay tiếng Anh) về đúng mã chuẩn kẻo lúc đếm bị tách thành 2 dòng
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
