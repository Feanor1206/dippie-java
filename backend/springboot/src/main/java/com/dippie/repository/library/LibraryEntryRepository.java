package com.dippie.repository.library;

import com.dippie.dto.library.LibraryItemView;
import com.dippie.model.library.LibraryEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// repo cha @NoRepositoryBean dùng generics với #{#entityName} để FavoriteItemRepository và WatchlistItemRepository khỏi lặp lại JPQL
@NoRepositoryBean
public interface LibraryEntryRepository<E extends LibraryEntry> extends JpaRepository<E, Long> {

    // join thẳng sang Movie để tên phim với poster luôn ăn bản mới nhất khi metadata đổi
    @Query("""
            select new com.dippie.dto.library.LibraryItemView(
                item.id,
                item.userId,
                item.movieId,
                movie.title,
                movie.poster,
                item.addedAt
            )
            from #{#entityName} item
            join Movie movie on movie.id = item.movieId
            where item.userId = :userId
            order by item.addedAt desc
            """)
    List<LibraryItemView> findViewsByUserIdOrderByAddedAtDesc(@Param("userId") Long userId);

    Optional<E> findByUserIdAndMovieId(Long userId, String movieId);

    @Query("select item.movieId from #{#entityName} item where item.userId = :userId")
    List<String> findMovieIdsByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);

    // xóa tài khoản thì gọi dọn sạch thư viện của user đó
    void deleteByUserId(Long userId);
}
