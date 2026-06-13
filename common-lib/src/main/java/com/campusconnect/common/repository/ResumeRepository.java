package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Resume;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code resumes} collection (Story 3.2). Tenant-aware — the student is authenticated,
 * so every read/write auto-scopes to the current tenant via {@link TenantAwareRepository}. All lookups
 * are keyed on {@code userId} (the principal); there is no cross-owner access path.
 */
@Repository
public class ResumeRepository extends TenantAwareRepository<Resume> {

    public ResumeRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Resume.class);
    }

    /** The student's current active resume, if any (tenant-scoped). */
    public Optional<Resume> findActiveByUserId(String userId) {
        return find(new Query(Criteria.where("userId").is(userId).and("isActive").is(true)))
                .stream().findFirst();
    }

    /** All of the student's resume versions, tenant-scoped. */
    public List<Resume> findByUserId(String userId) {
        return find(new Query(Criteria.where("userId").is(userId)));
    }

    /** Next version number for this student (max existing + 1; 1 if none). */
    public int nextVersionFor(String userId) {
        return findByUserId(userId).stream().map(Resume::getVersion).max(Comparator.naturalOrder()).orElse(0) + 1;
    }
}
