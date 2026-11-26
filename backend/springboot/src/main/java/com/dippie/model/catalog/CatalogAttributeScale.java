package com.dippie.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

// thang đo thuộc tính nội dung (ràng buộc minimum < maximum), kế thừa khung cột chung từ CatalogRecord
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

    // chép đè thông tin thang đo mới import sang bản ghi đang nằm trong DB
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
}
