package com.dippie.service.catalog;

import com.dippie.model.movie.MovieGenre;
import com.dippie.model.movie.MovieGenreTerm;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// đồng bộ bảng từ vựng movie_genre_terms với bảng nối movie_genres cho bảng movies cũ
@Repository
public class CatalogMovieGenres {

    private final EntityManager entities;

    public CatalogMovieGenres(EntityManager entities) {
        this.entities = entities;
    }

    // chèn mấy tên thể loại còn thiếu trước rồi mới xóa liên kết thừa và thêm liên kết mới cho từng phim
    public void sync(Map<String, Set<String>> genresByMovie) {
        if (genresByMovie.isEmpty()) {
            return;
        }
        // nhớ flush MovieGenreTerm trước khi ghi MovieGenre nha, không là vướng khóa ngoại tên thể loại
        Set<String> terms = new HashSet<>();
        genresByMovie.values().forEach(terms::addAll);
        var existingTerms = entities.createQuery("select term.name from MovieGenreTerm term where term.name in :names",
                String.class).setParameter("names", terms).getResultList();
        terms.removeAll(existingTerms);
        terms.forEach(term -> entities.persist(new MovieGenreTerm(term)));
        entities.flush();

        // gỡ mấy liên kết cũ không còn trong danh sách mới rồi chèn phần còn thiếu
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
