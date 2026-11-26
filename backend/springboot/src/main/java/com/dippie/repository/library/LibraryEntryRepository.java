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

    // join thẳng sang Movie theo đúng hồ sơ đang mở để tên phim với poster luôn ăn bản mới nhất
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
            where item.userId = :userId and item.profileScopeId = :profileScopeId
            order by item.addedAt desc
            """)
    List<LibraryItemView> findViewsByUserIdAndProfileScopeIdOrderByAddedAtDesc(
            @Param("userId") Long userId,
            @Param("profileScopeId") long profileScopeId
    );

    Optional<E> findByUserIdAndProfileScopeIdAndMovieId(Long userId, long profileScopeId, String movieId);

    // bản cũ tra theo hồ sơ chính (profileScopeId = 0) để giữ tương thích test cũ
    @Query("select item from #{#entityName} item where item.userId = :userId and item.profileScopeId = 0 and item.movieId = :movieId")
    Optional<E> findByUserIdAndMovieId(@Param("userId") Long userId, @Param("movieId") String movieId);

    @Query("select item.movieId from #{#entityName} item where item.userId = :userId and item.profileScopeId = :profileScopeId")
    List<String> findMovieIdsByUserIdAndProfileScopeId(@Param("userId") Long userId, @Param("profileScopeId") long profileScopeId);

    void deleteByUserIdAndProfileScopeIdAndMovieId(Long userId, long profileScopeId, String movieId);

    // xóa riêng dữ liệu của 1 hồ sơ bé khi xóa hồ sơ đó
    void deleteByUserIdAndProfileScopeId(Long userId, long profileScopeId);

    // xóa tài khoản thì gọi dọn sạch thư viện của cả hồ sơ chính lẫn hồ sơ bé
    void deleteByUserId(Long userId);
}
