package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An append-only audit record of a sensitive admin action (Story 3.3, architecture §5/§10/§11).
 * Tenant-scoped; written once and never updated or deleted. {@code oldValue}/{@code newValue} capture
 * the before/after of the change (compact human-readable strings).
 */
@Document("auditLogs")
@CompoundIndex(name = "idx_tenant_entity", def = "{'tenantId': 1, 'entityType': 1, 'entityId': 1}")
public class AuditLog extends TenantAwareDocument {

    private String actor;
    private String action;
    private String entityType;
    private String entityId;
    private String oldValue;
    private String newValue;

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
}
