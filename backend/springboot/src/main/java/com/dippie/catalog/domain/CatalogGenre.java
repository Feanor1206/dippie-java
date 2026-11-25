package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_genres")
public class CatalogGenre extends CatalogRecord {

    @Column(name = "labels_json", columnDefinition = "LONGTEXT", nullable = false)
    private String labelsJson;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    protected CatalogGenre() {
    }

    public void applyImport(CatalogGenre imported) {
        replaceRecordFrom(imported);
        labelsJson = imported.labelsJson;
        aliasesJson = imported.aliasesJson;
        description = imported.description;
    }

    public CatalogGenre(String id, String labelsJson, String aliasesJson, String description, String recordJson,
            String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.labelsJson = labelsJson;
        this.aliasesJson = aliasesJson;
        this.description = description;
    }

    public String getLabelsJson() {
        return labelsJson;
    }

    public String getAliasesJson() {
        return aliasesJson;
    }

    public String getDescription() {
        return description;
    }
}
