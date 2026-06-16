package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Offer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

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
}
