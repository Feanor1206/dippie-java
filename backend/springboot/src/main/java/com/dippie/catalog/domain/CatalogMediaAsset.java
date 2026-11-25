package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "catalog_media_assets")
@Check(name = "ck_catalog_asset_policy", constraints = "access_policy IN ('private', 'authenticated', 'public')")
@Check(name = "ck_catalog_asset_content", constraints = "content_type IN ('video', 'audio')")
@Check(name = "ck_catalog_asset_role",
        constraints = "role IN ('feature', 'trailer', 'extra', 'opening', 'ending', 'dub', 'audio_description')")
@Check(name = "ck_catalog_asset_size", constraints = "size_bytes >= 0")
@Check(name = "ck_catalog_asset_duration", constraints = "duration_ms > 0")
public class CatalogMediaAsset extends CatalogRecord {

    @Column(name = "content_type", length = 50, nullable = false)
    @ColumnDefault("'video'")
    private String contentType;

    @Column(name = "role", length = 50, nullable = false)
    @ColumnDefault("'feature'")
    private String role;

    @Column(name = "edition_label", length = 100)
    private String editionLabel;

    @Column(name = "spoken_languages_json", columnDefinition = "LONGTEXT")
    private String spokenLanguagesJson;

    @Column(name = "embedded_subtitle_languages_json", columnDefinition = "LONGTEXT")
    private String embeddedSubtitleLanguagesJson;

    @Column(name = "storage_provider", length = 50, nullable = false)
    private String storageProvider;

    @Column(name = "storage_key", length = 100, nullable = false)
    private String storageKey;

    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "drive_file_id", length = 255)
    private String driveFileId;

    @Column(name = "drive_folder_id", length = 255)
    private String driveFolderId;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", length = 100)
    private String sha256;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "technical_json", columnDefinition = "LONGTEXT")
    private String technicalJson;

    @Column(name = "coverage_json", columnDefinition = "LONGTEXT")
    private String coverageJson;

    @Column(name = "related_asset_id", length = 100)
    private String relatedAssetId;

    @Column(name = "access_policy", length = 50, nullable = false)
    @ColumnDefault("'public'")
    private String accessPolicy;

    @Column(name = "enabled", nullable = false)
    @ColumnDefault("TRUE")
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_asset_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_asset_related"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CatalogMediaAsset relatedAsset;

    protected CatalogMediaAsset() {
    }

    public void applyImport(CatalogMediaAsset imported) {
        replaceRecordFrom(imported);
        contentType = imported.contentType;
        role = imported.role;
        editionLabel = imported.editionLabel;
        storageProvider = imported.storageProvider;
        storageKey = imported.storageKey;
        relativePath = imported.relativePath;
        driveFileId = imported.driveFileId;
        driveFolderId = imported.driveFolderId;
        sizeBytes = imported.sizeBytes;
        sha256 = imported.sha256;
        durationMs = imported.durationMs;
        technicalJson = imported.technicalJson;
        coverageJson = imported.coverageJson;
        accessPolicy = imported.accessPolicy;
        enabled = imported.enabled;
    }

    public void linkRelatedAsset(String assetId) {
        relatedAssetId = assetId;
    }

    public CatalogMediaAsset(String id, String contentType, String role, String editionLabel,
            String spokenLanguagesJson, String embeddedSubtitleLanguagesJson, String storageProvider, String storageKey,
            String relativePath, String driveFileId, String driveFolderId, Long sizeBytes, String sha256,
            Long durationMs, String technicalJson, String coverageJson, String relatedAssetId, String accessPolicy,
            boolean enabled, String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.contentType = Objects.requireNonNullElse(contentType, "video");
        this.role = Objects.requireNonNullElse(role, "feature");
        this.editionLabel = editionLabel;
        this.spokenLanguagesJson = spokenLanguagesJson;
        this.embeddedSubtitleLanguagesJson = embeddedSubtitleLanguagesJson;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.relativePath = relativePath;
        this.driveFileId = driveFileId;
        this.driveFolderId = driveFolderId;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.durationMs = durationMs;
        this.technicalJson = technicalJson;
        this.coverageJson = coverageJson;
        this.relatedAssetId = relatedAssetId;
        this.accessPolicy = Objects.requireNonNullElse(accessPolicy, "public");
        this.enabled = enabled;
    }

    public String getContentType() {
        return contentType;
    }

    public String getRole() {
        return role;
    }

    public String getEditionLabel() {
        return editionLabel;
    }

    public String getSpokenLanguagesJson() {
        return spokenLanguagesJson;
    }

    public String getEmbeddedSubtitleLanguagesJson() {
        return embeddedSubtitleLanguagesJson;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getTechnicalJson() {
        return technicalJson;
    }

    public String getCoverageJson() {
        return coverageJson;
    }

    public String getRelatedAssetId() {
        return relatedAssetId;
    }

    public String getAccessPolicy() {
        return accessPolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAccessibleBy(boolean authenticated, boolean admin) {
        return enabled
                && ((authenticated && admin) || PublicationStatus.of(getRecordJson()).isPublished())
                && AccessPolicy.of(accessPolicy).allows(authenticated, admin);
    }

    public boolean isEpisodeVideo() {
        return "video".equalsIgnoreCase(contentType)
                && !"dub".equalsIgnoreCase(role) && !"audio_description".equalsIgnoreCase(role);
    }

    public boolean coversFullEpisode(String episodeId) {
        if (episodeId == null) {
            return false;
        }
        return JsonText.parse(coverageJson)
                .filter(JsonNode::isArray)
                .map(coverage -> containsFullEpisode(coverage, episodeId))
                .orElse(false);
    }

    private static boolean containsFullEpisode(JsonNode coverage, String episodeId) {
        for (JsonNode entry : coverage) {
            if (episodeId.equals(entry.path("episode_id").textValue())
                    && entry.path("scope").asText().toLowerCase(Locale.ROOT).equals("full_episode")) {
                return true;
            }
        }
        return false;
    }
}
