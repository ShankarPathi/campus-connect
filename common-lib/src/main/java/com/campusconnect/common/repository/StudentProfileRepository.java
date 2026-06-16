package com.campusconnect.common.repository;

import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code studentProfiles} collection (Story 3.1). Extends {@link TenantAwareRepository}
 * because the student is always authenticated here (unlike the public recruiter-registration flow), so
 * {@link com.campusconnect.common.tenancy.TenantContext} is bound and every read/write auto-scopes to the
 * current tenant. {@link #findByStudentId} builds on the tenant-aware {@code find} so it can never reach
 * another tenant's profile.
 */
@Repository
public class StudentProfileRepository extends TenantAwareRepository<StudentProfile> {

    public StudentProfileRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, StudentProfile.class);
    }

    /** The current tenant's profile for one student, if any. Tenant-scoped via the base {@code find}. */
    public Optional<StudentProfile> findByStudentId(String studentId) {
        return find(new Query(Criteria.where("studentId").is(studentId))).stream().findFirst();
    }

    /** The current tenant's profiles in a given approval status — backs the admin review queue (Story 3.3). */
    public List<StudentProfile> findByApprovalStatus(ProfileApprovalStatus status) {
        return find(new Query(Criteria.where("profileApprovalStatus").is(status)));
    }

    /**
     * The current tenant's profiles for the given students — a single batch load (Story 6.1) that maps a
     * drive's applicants to their hiring profiles without an N+1. Tenant-scoped via {@code find}; an empty
     * input short-circuits to no query.
     */
    public List<StudentProfile> findByStudentIdIn(Collection<String> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return find(new Query(Criteria.where("studentId").in(studentIds)));
    }

    /**
     * Sets {@code isLocked} on <b>every</b> profile in the current tenant in one update (Story 3.4 season
     * lock/unlock). Built on the base {@link #tenantCriteria()} so it can never cross tenants. Touches only
     * {@code isLocked} (and {@code updatedAt}) — {@code profileApprovalStatus} is left untouched (approval ≠
     * lock). Returns the number of profiles modified, for the audit row.
     */
    public long setLockedForTenant(boolean locked) {
        Update update = Update.update("isLocked", locked).set("updatedAt", Instant.now());
        return mongoTemplate.updateMulti(new Query(tenantCriteria()), update, type).getModifiedCount();
    }

    /**
     * Sets {@code isPlaced} on one student's profile in the current tenant (Story 7.3 accept). A <b>targeted</b>
     * field update (not a read-modify-write full-document save), so it touches only {@code isPlaced} (and
     * {@code updatedAt}) and can never clobber a concurrent change to another profile field (the Story 3.4
     * lost-update concern). Tenant-scoped via {@link #tenantCriteria()}. Returns the number modified (0 if the
     * student has no profile).
     */
    public long setPlaced(String studentId, boolean placed) {
        Query query = new Query(Criteria.where("studentId").is(studentId)).addCriteria(tenantCriteria());
        Update update = Update.update("isPlaced", placed).set("updatedAt", Instant.now());
        return mongoTemplate.updateFirst(query, update, type).getModifiedCount();
    }
}
