package com.dippie.model.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// bảng từ điển tên thể loại chuẩn cho movie_genres trỏ khóa ngoại sang
@Entity
@Table(name = "movie_genre_terms")
public class MovieGenreTerm {

    @Id
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    protected MovieGenreTerm() {
    }

    public MovieGenreTerm(String name) {
        this.name = name;
    }
}
