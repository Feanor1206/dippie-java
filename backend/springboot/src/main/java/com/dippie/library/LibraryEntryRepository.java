package com.dippie.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Repo chung @NoRepositoryBean dùng generics với #{#entityName} để FavoriteItemRepository và WatchlistItemRepository không phải viết lại JPQL giống nhau.
@NoRepositoryBean
public interface LibraryEntryRepository<E extends LibraryEntry> extends JpaRepository<E, Long> {

    // Join thẳng sang bảng Movie để tên phim và poster luôn lấy bản mới nhất khi metadata phim được cập nhật.
    @Query("""
            select new com.dippie.library.LibraryItemView(
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

    // Gọi lúc xóa tài khoản để dọn sạch các mục thư viện thuộc user đó.
    void deleteByUserId(Long userId);
}
