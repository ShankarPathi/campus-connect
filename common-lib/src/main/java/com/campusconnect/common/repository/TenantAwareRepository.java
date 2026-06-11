package com.campusconnect.common.repository;

import com.campusconnect.common.domain.TenantAwareDocument;
import com.campusconnect.common.tenancy.TenantContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Base repository for tenant-scoped entities. <b>This is the single place tenant isolation lives</b>
 * (architecture §4): every write auto-stamps {@code tenantId} from {@link TenantContext} (when
 * absent) and every read auto-appends a {@code tenantId} criterion. A developer using these standard
 * methods cannot write a tenant-leaking query.
 *
 * <p>Subclasses are plain {@code @Repository} components: {@code super(mongoTemplate, MyDoc.class)}.
 * For bespoke queries, build on {@link #withTenant(Query)} / {@link #tenantCriteria()} so they stay
 * scoped too.
 *
 * @param <T> the tenant-aware document type
 */
public abstract class TenantAwareRepository<T extends TenantAwareDocument> {

    protected final MongoTemplate mongoTemplate;
    protected final Class<T> type;

    protected TenantAwareRepository(MongoTemplate mongoTemplate, Class<T> type) {
        this.mongoTemplate = mongoTemplate;
        this.type = type;
    }

    /**
     * Saves the entity under the current tenant. The {@code tenantId} is <b>always</b> stamped from
     * {@link TenantContext} (a caller-supplied value is ignored — never trusted), and an update of an
     * existing id is allowed only if that document already belongs to the current tenant. This closes
     * the cross-tenant write/overwrite vector that a bare {@code mongoTemplate.save} (upsert by
     * {@code _id}) would otherwise open.
     *
     * <p>(Hardened beyond architecture §4's literal "stamp if absent": always-stamp + tenant-scoped
     * update — a document loaded via the scoped {@link #findById(String)} already carries the matching
     * tenant, so no legitimate flow is affected.)
     */
    public T save(T entity) {
        String tenantId = TenantContext.requireTenantId();
        entity.setTenantId(tenantId);
        Instant now = Instant.now();

        if (entity.getId() == null) {
            // new document
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(now);
            }
            entity.setUpdatedAt(now);
            return mongoTemplate.insert(entity);
        }

        // existing id: refuse to upsert over a document that is not this tenant's
        T existing = mongoTemplate.findOne(
                new Query(Criteria.where("id").is(entity.getId())).addCriteria(tenantCriteria()), type);
        if (existing == null) {
            throw new IllegalStateException(
                    "Cannot save document id " + entity.getId() + " — it does not belong to the current tenant");
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(existing.getCreatedAt());
        }
        entity.setUpdatedAt(now);
        return mongoTemplate.save(entity);
    }

    /** Finds by id, scoped to the current tenant — another tenant's document is never returned. */
    public Optional<T> findById(String id) {
        Query query = new Query(Criteria.where("id").is(id)).addCriteria(tenantCriteria());
        return Optional.ofNullable(mongoTemplate.findOne(query, type));
    }

    /** All documents for the current tenant. */
    public List<T> findAll() {
        return mongoTemplate.find(new Query(tenantCriteria()), type);
    }

    /** Runs a caller-supplied query with the tenant criterion appended. */
    public List<T> find(Query query) {
        return mongoTemplate.find(withTenant(query), type);
    }

    /** Count for the current tenant. */
    public long count() {
        return mongoTemplate.count(new Query(tenantCriteria()), type);
    }

    /** Whether a document with this id exists for the current tenant. */
    public boolean existsById(String id) {
        Query query = new Query(Criteria.where("id").is(id)).addCriteria(tenantCriteria());
        return mongoTemplate.exists(query, type);
    }

    /** Deletes by id, scoped to the current tenant. */
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id)).addCriteria(tenantCriteria());
        mongoTemplate.remove(query, type);
    }

    /**
     * Appends the current-tenant criterion to a query — use for any custom query in a subclass.
     * The caller's query must NOT already contain a {@code tenantId} criterion ({@code addCriteria}
     * rejects a duplicate key); {@code tenantId} is reserved by this base.
     */
    protected Query withTenant(Query query) {
        return query.addCriteria(tenantCriteria());
    }

    /** The {@code tenantId = <current tenant>} criterion. Throws if no tenant is bound. */
    protected Criteria tenantCriteria() {
        return Criteria.where("tenantId").is(TenantContext.requireTenantId());
    }
}
