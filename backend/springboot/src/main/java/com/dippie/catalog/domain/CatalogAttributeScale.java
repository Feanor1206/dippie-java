package com.dippie.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "catalog_attribute_scales")
@Check(name = "ck_catalog_scale_bounds", constraints = "minimum < maximum")
public class CatalogAttributeScale extends CatalogRecord {

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "definition", columnDefinition = "LONGTEXT")
    private String definition;

    @Column(name = "minimum", nullable = false)
    private double minimum;

    @Column(name = "maximum", nullable = false)
    private double maximum;

    @Column(name = "levels_json", columnDefinition = "LONGTEXT", nullable = false)
    private String levelsJson;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "source_ids_json", columnDefinition = "LONGTEXT")
    private String sourceIdsJson;

    protected CatalogAttributeScale() {
    }

    public void applyImport(CatalogAttributeScale imported) {
        replaceRecordFrom(imported);
        name = imported.name;
        definition = imported.definition;
        minimum = imported.minimum;
        maximum = imported.maximum;
        levelsJson = imported.levelsJson;
        version = imported.version;
        sourceIdsJson = imported.sourceIdsJson;
    }

    public CatalogAttributeScale(String id, String name, String definition, double minimum, double maximum,
            String levelsJson, String version, String sourceIdsJson, String recordJson, String rawJson, int revision) {
        super(id, recordJson, rawJson, revision);
        this.name = name;
        this.definition = definition;
        this.minimum = minimum;
        this.maximum = maximum;
        this.levelsJson = levelsJson;
        this.version = version;
        this.sourceIdsJson = sourceIdsJson;
    }

    public String getName() {
        return name;
    }

    public String getDefinition() {
        return definition;
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public String getLevelsJson() {
        return levelsJson;
    }

    public String getVersion() {
        return version;
    }

    public String getSourceIdsJson() {
        return sourceIdsJson;
    }
}
