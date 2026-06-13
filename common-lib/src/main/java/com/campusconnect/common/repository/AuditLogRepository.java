package com.campusconnect.common.repository;

import com.campusconnect.common.domain.AuditLog;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the append-only {@code auditLogs} collection (Story 3.3). Tenant-aware; exposes only
 * write ({@code save}, inherited) and tenant-scoped reads — no update/delete beyond the base, keeping
 * the trail append-only by convention.
 */
@Repository
public class AuditLogRepository extends TenantAwareRepository<AuditLog> {

    public AuditLogRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, AuditLog.class);
    }

    /** All audit rows for one entity, tenant-scoped. */
    public List<AuditLog> findByEntity(String entityType, String entityId) {
        return find(new Query(Criteria.where("entityType").is(entityType).and("entityId").is(entityId)));
    }
}
