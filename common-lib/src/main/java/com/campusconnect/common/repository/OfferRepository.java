package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Offer;
import com.campusconnect.common.domain.OfferStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code offers} collection (Story 7.1, FR-23). Extends {@link TenantAwareRepository}, so
 * every read auto-scopes to the current tenant. The single bespoke read is the idempotency pre-check —
 * "does this application already have an offer?" — which, together with the unique {@code {tenantId,
 * applicationId}} index, enforces one offer per application.
 */
@Repository
public class OfferRepository extends TenantAwareRepository<Offer> {

    public OfferRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Offer.class);
    }

    /**
     * The offer for one application, if any (tenant-scoped via {@code find}) — the release pre-check. A
     * present value means an offer was already released for this application → 409 {@code CONFLICT}.
     */
    public Optional<Offer> findByApplicationId(String applicationId) {
        return find(new Query(Criteria.where("applicationId").is(applicationId))).stream().findFirst();
    }

    /**
     * All {@code PENDING} offers whose {@code acceptanceDeadline} is at or before {@code now}, <b>across every
     * tenant</b> — the Story 7.2 offer-expiry job's enumeration. This is a deliberate <b>system, cross-tenant</b>
     * read on the raw {@code mongoTemplate} (NOT the tenant-scoped {@link #find}), because a scheduled job runs
     * with no bound tenant. It is the ONLY un-tenant-filtered read in the codebase and MUST be invoked solely by
     * the system job — never from a request path. The job then binds each offer's own tenant before writing, so
     * tenant isolation is preserved on the write path.
     */
    public List<Offer> findExpirable(Instant now) {
        Query query = new Query(Criteria.where("status").is(OfferStatus.PENDING)
                .and("acceptanceDeadline").lte(now));
        return mongoTemplate.find(query, Offer.class);
    }

    /**
     * One offer by id <b>scoped to a student</b> (tenant + owner scoped) — the 404 guard for the student's
     * offer view/accept/decline (Story 7.3). Builds on the tenant-scoped {@code findById}, then narrows by
     * {@code studentId}, so another student's offer (same tenant) and another tenant's both resolve to empty.
     */
    public Optional<Offer> findByIdAndStudentId(String id, String studentId) {
        return findById(id).filter(o -> studentId.equals(o.getStudentId()));
    }

    /** The current tenant's offers for one student — the student's "my offers" list (Story 7.3). */
    public List<Offer> findByStudentId(String studentId) {
        return find(new Query(Criteria.where("studentId").is(studentId)));
    }
}
