package com.dippie.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, Long> {

    @Query("""
            select new com.dippie.library.LibraryItemView(
                item.id,
                item.userId,
                item.movieId,
                movie.title,
                movie.poster,
                item.addedAt
            )
            from FavoriteItem item
            join Movie movie on movie.id = item.movieId
            where item.userId = :userId
            order by item.addedAt desc
            """)
    List<LibraryItemView> findViewsByUserIdOrderByAddedAtDesc(@Param("userId") Long userId);

    Optional<FavoriteItem> findByUserIdAndMovieId(Long userId, String movieId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);
}
