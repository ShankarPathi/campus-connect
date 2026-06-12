package com.campusconnect.common.repository;

import com.campusconnect.common.domain.RecruiterProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Round-trip, tenant-scoped lookup (isolation), batch load, and the unique {tenantId,userId} index. */
class RecruiterProfileRepositoryTest extends AbstractMongoIT {

    RecruiterProfileRepository repository;

    @BeforeAll
    static void indexes() {
        ensureIndexes(RecruiterProfile.class);
    }

    @BeforeEach
    void setUp() {
        repository = new RecruiterProfileRepository(mongoTemplate);
        mongoTemplate.remove(new Query(), RecruiterProfile.class);
    }

    @Test
    void save_thenFindByUserIdAndTenantId_roundTrips() {
        repository.save(profile("user-1", "tenant-a", "Acme Corp"));

        RecruiterProfile found = repository.findByUserIdAndTenantId("user-1", "tenant-a").orElseThrow();
        assertThat(found.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findByUserIdAndTenantId_isTenantScoped_isolatesAcrossTenants() {
        repository.save(profile("user-1", "tenant-a", "Acme Corp"));

        // same userId but a different tenant must not see it
        assertThat(repository.findByUserIdAndTenantId("user-1", "tenant-b")).isEmpty();
    }

    @Test
    void findByTenantIdAndUserIdIn_batchLoadsWithinTenant() {
        repository.save(profile("u1", "tenant-a", "A"));
        repository.save(profile("u2", "tenant-a", "B"));
        repository.save(profile("u3", "tenant-b", "C")); // other tenant — excluded

        List<RecruiterProfile> found = repository.findByTenantIdAndUserIdIn("tenant-a", List.of("u1", "u2", "u3"));

        assertThat(found).extracting(RecruiterProfile::getUserId).containsExactlyInAnyOrder("u1", "u2");
    }

    @Test
    void deleteByUserIdAndTenantId_removesProfile_scopedToTenant() {
        repository.save(profile("user-1", "tenant-a", "Acme Corp"));
        repository.save(profile("user-1", "tenant-b", "Other Co")); // same userId, different tenant

        repository.deleteByUserIdAndTenantId("user-1", "tenant-a");

        assertThat(repository.findByUserIdAndTenantId("user-1", "tenant-a")).isEmpty();
        // the other tenant's profile is untouched
        assertThat(repository.findByUserIdAndTenantId("user-1", "tenant-b")).isPresent();
    }

    @Test
    void duplicateTenantAndUser_violatesUniqueIndex() {
        repository.save(profile("user-1", "tenant-a", "Acme Corp"));

        assertThatThrownBy(() -> repository.save(profile("user-1", "tenant-a", "Other Name")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private static RecruiterProfile profile(String userId, String tenantId, String companyName) {
        RecruiterProfile p = new RecruiterProfile();
        p.setUserId(userId);
        p.setTenantId(tenantId);
        p.setCompanyName(companyName);
        return p;
    }
}
