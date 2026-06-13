package com.campusconnect.common.audit;

import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.AuditLog;
import com.campusconnect.common.repository.AuditLogRepository;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Records audit-trail entries (Story 3.3). One seam so every audited action is written identically:
 * the {@code actor} is the current principal and the row is stamped with the current tenant by the
 * tenant-aware repository. Synchronous (shared DB) — the caller writes the audit row in the same flow
 * as the action it records.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(AuditAction action, String entityType, String entityId, String oldValue, String newValue) {
        AuditLog entry = new AuditLog();
        entry.setActor(TenantContext.getUserId());
        entry.setAction(action.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        auditLogRepository.save(entry);
    }
}
