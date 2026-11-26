package com.dippie.model.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

// Bảng nối movie_genres giữa phim và tên thể loại, dùng khóa chính ghép MovieGenre.Key.
@Entity
@Table(name = "movie_genres",
        indexes = {
                @Index(name = "idx_movie_genre_name", columnList = "genre_name")
        })
@IdClass(MovieGenre.Key.class)
public class MovieGenre {

    @Id
    @Column(name = "movie_id", length = 100, nullable = false)
    private String movieId;

    @Id
    @Column(name = "genre_name", length = 255, nullable = false)
    private String genreName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_movie_genres_movie_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_name", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_movie_genres_genre_name"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MovieGenreTerm term;

    protected MovieGenre() {
    }

    public MovieGenre(String movieId, String genreName) {
        this.movieId = movieId;
        this.genreName = genreName;
    }

    public Key key() {
        return new Key(movieId, genreName);
    }

    // Khóa chính ghép (movieId, genreName) cho @IdClass của bảng movie_genres.
    public record Key(String movieId, String genreName) implements Serializable {
    }
}
