package com.dippie.catalog.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_record", columnList = "table_name, record_id, action_at")
        })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "table_name", length = 64, nullable = false)
    private String tableName;

    @Column(name = "record_id", length = 100, nullable = false)
    private String recordId;

    @Column(name = "operation", length = 8, nullable = false)
    private String operation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "JSON", nullable = false)
    private JsonNode oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "JSON")
    private JsonNode newValues;

    @Column(name = "action_at", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime actionAt;

    @Column(name = "mysql_user", length = 288, nullable = false)
    private String mysqlUser;

    protected AuditLog() {
    }

    public AuditLog(String tableName, String recordId, String operation, JsonNode oldValues, JsonNode newValues,
            String mysqlUser) {
        this.tableName = tableName;
        this.recordId = recordId;
        this.operation = operation;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.mysqlUser = mysqlUser;
    }

    public Long getId() {
        return id;
    }

    public String getTableName() {
        return tableName;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getOperation() {
        return operation;
    }

    public JsonNode getOldValues() {
        return oldValues;
    }

    public JsonNode getNewValues() {
        return newValues;
    }

    public LocalDateTime getActionAt() {
        return actionAt;
    }

    public String getMysqlUser() {
        return mysqlUser;
    }

    @PrePersist
    private void initializeTimestamps() {
        actionAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
