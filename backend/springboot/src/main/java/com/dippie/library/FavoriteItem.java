package com.dippie.library;

import com.dippie.movie.Movie;
import com.dippie.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "favorite_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_movie_favorite", columnNames = {"user_id", "movie_id"})
        },
        indexes = {
                @Index(name = "idx_favorite_movie", columnList = "movie_id")
        }
)
public class FavoriteItem extends LibraryEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "movie_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_favorite_movie")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Movie movie;

    protected FavoriteItem() {
    }

    public FavoriteItem(Long userId, String movieId, LocalDateTime addedAt) {
        super(userId, movieId, addedAt);
    }
}
