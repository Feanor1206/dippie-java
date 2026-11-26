package com.dippie.service.movie;

import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.movie.Movie;
import com.dippie.service.catalog.MoviePresentationLookup;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

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
