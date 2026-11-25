package com.dippie.catalog.importing;

import com.dippie.movie.MovieGenre;
import com.dippie.movie.MovieGenreTerm;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Đồng bộ bảng từ vựng movie_genre_terms và bảng nối movie_genres cho các hàng trong bảng movies cũ.
@Repository
class CatalogMovieGenres {

    private final EntityManager entities;

    CatalogMovieGenres(EntityManager entities) {
        this.entities = entities;
    }

    // Chèn các tên thể loại còn thiếu trước rồi mới xóa liên kết thừa và bổ sung liên kết mới cho từng phim.
    void sync(Map<String, Set<String>> genresByMovie) {
        if (genresByMovie.isEmpty()) {
            return;
        }
        // Phải flush MovieGenreTerm trước khi ghi MovieGenre để thỏa mãn khóa ngoại tên thể loại.
        Set<String> terms = new HashSet<>();
        genresByMovie.values().forEach(terms::addAll);
        var existingTerms = entities.createQuery("select term.name from MovieGenreTerm term where term.name in :names",
                String.class).setParameter("names", terms).getResultList();
        terms.removeAll(existingTerms);
        terms.forEach(term -> entities.persist(new MovieGenreTerm(term)));
        entities.flush();

        // Gỡ bỏ các liên kết cũ không còn trong danh sách mới, rồi chèn thêm phần còn thiếu.
        Set<MovieGenre.Key> existingLinks = new HashSet<>();
        var links = entities.createQuery("select link from MovieGenre link where link.movieId in :ids", MovieGenre.class)
                .setParameter("ids", genresByMovie.keySet()).getResultList();
        for (MovieGenre link : links) {
            var key = link.key();
            if (genresByMovie.get(key.movieId()).contains(key.genreName())) {
                existingLinks.add(key);
            } else {
                entities.remove(link);
            }
        }
        genresByMovie.forEach((movie, genres) -> genres.stream()
                .filter(genre -> !existingLinks.contains(new MovieGenre.Key(movie, genre)))
                .forEach(genre -> entities.persist(new MovieGenre(movie, genre))));
    }
}
