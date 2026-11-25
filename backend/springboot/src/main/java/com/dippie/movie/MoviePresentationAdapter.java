package com.dippie.movie;

import com.dippie.catalog.domain.MoviePresentationLookup;
import com.dippie.catalog.domain.PublicationStatus;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Adapter cài MoviePresentationLookup để bên catalog.domain lấy rating và ảnh từ bảng movies mà không phụ thuộc thẳng vào repo của gói movie.
@Component
public class MoviePresentationAdapter implements MoviePresentationLookup {

    private final EntityManager entities;

    public MoviePresentationAdapter(EntityManager entities) {
        this.entities = entities;
    }

    // Join với CatalogEpisode để chỉ kéo thông tin hiển thị của các tập đã ở trạng thái READY.
    @Override
    @Transactional(readOnly = true)
    public Map<String, Presentation> findForReadyEpisodes() {
        Map<String, Presentation> presentations = new HashMap<>();
        for (Movie movie : entities.createQuery("""
                select movie from Movie movie join CatalogEpisode episode on episode.id = movie.id
                where episode.publicationStatus = :status
                """, Movie.class).setParameter("status", PublicationStatus.READY_VALUE).getResultList()) {
            presentations.put(movie.getId(), new Presentation(movie.getRating(), movie.getPoster(), movie.getBackdrop()));
        }
        return Map.copyOf(presentations);
    }
}
