package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "catalog_sources")
@Check(name = "ck_catalog_source_kind",
        constraints = "kind IN ('manual', 'official', 'external_catalog', 'subtitle', 'transcript', 'video_observation', "
                + "'model_output', 'other')")
public class CatalogSource extends CatalogRecord {

    @Column(name = "kind", length = 50, nullable = false)
    private String kind;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "uri", length = 1000)
    private String uri;

    @Column(name = "note", columnDefinition = "LONGTEXT")
    private String note;

    @Column(name = "retrieved_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime retrievedAt;

    protected CatalogSource() {
    }

    public void applyImport(CatalogSource imported) {
        replaceRecordFrom(imported);
        kind = imported.kind;
        title = imported.title;
        uri = imported.uri;
        note = imported.note;
        retrievedAt = imported.retrievedAt;
    }

    public CatalogSource(String id, String kind, String title, String uri, String note, LocalDateTime retrievedAt,
            String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.kind = kind;
        this.title = title;
        this.uri = uri;
        this.note = note;
        this.retrievedAt = retrievedAt;
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getUri() {
        return uri;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getRetrievedAt() {
        return retrievedAt;
    }
}
