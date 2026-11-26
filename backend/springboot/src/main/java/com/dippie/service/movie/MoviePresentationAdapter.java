package com.dippie.service.movie;

import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.movie.Movie;
import com.dippie.service.catalog.MoviePresentationLookup;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

// kiểu Adapter cài MoviePresentationLookup cho bên catalog.domain lấy rating với ảnh từ bảng movies mà khỏi đụng thẳng repo gói movie
@Component
public class MoviePresentationAdapter implements MoviePresentationLookup {

    private final EntityManager entities;

    public MoviePresentationAdapter(EntityManager entities) {
        this.entities = entities;
    }

    // join CatalogEpisode để chỉ kéo ảnh với rating của mấy tập đã READY
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
