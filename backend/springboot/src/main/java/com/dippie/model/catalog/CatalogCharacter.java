package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// nhân vật của các phim bộ trong catalog, kế thừa cột id, JSON với revision từ CatalogRecord
@Entity
@Table(name = "catalog_characters")
public class CatalogCharacter extends CatalogRecord {

    @Column(name = "series_ids_json", columnDefinition = "LONGTEXT")
    private String seriesIdsJson;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "aliases_json", columnDefinition = "LONGTEXT")
    private String aliasesJson;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "traits_json", columnDefinition = "LONGTEXT")
    private String traitsJson;

    @Column(name = "relationships_json", columnDefinition = "LONGTEXT")
    private String relationshipsJson;

    @Column(name = "voice_credits_json", columnDefinition = "LONGTEXT")
    private String voiceCreditsJson;

    @Column(name = "artworks_json", columnDefinition = "LONGTEXT")
    private String artworksJson;

    protected CatalogCharacter() {
    }

    // re-import thì chép lại mô tả, vai trò với quan hệ nhân vật, giữ nguyên voiceCreditsJson với artworksJson
    public void applyImport(CatalogCharacter imported) {
        replaceRecordFrom(imported);
        seriesIdsJson = imported.seriesIdsJson;
        name = imported.name;
        aliasesJson = imported.aliasesJson;
        description = imported.description;
        role = imported.role;
        traitsJson = imported.traitsJson;
        relationshipsJson = imported.relationshipsJson;
    }

    public CatalogCharacter(String id, String seriesIdsJson, String name, String aliasesJson, String description,
            String role, String traitsJson, String relationshipsJson, String voiceCreditsJson, String artworksJson,
            String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.seriesIdsJson = seriesIdsJson;
        this.name = name;
        this.aliasesJson = aliasesJson;
        this.description = description;
        this.role = role;
        this.traitsJson = traitsJson;
        this.relationshipsJson = relationshipsJson;
        this.voiceCreditsJson = voiceCreditsJson;
        this.artworksJson = artworksJson;
    }

    public String getSeriesIdsJson() {
        return seriesIdsJson;
    }

    public String getName() {
        return name;
    }

    public String getAliasesJson() {
        return aliasesJson;
    }
}
