package com.campusconnect.common.repository;

import com.campusconnect.common.domain.ProfileApprovalStatus;
import com.campusconnect.common.domain.StudentProfile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

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
}
