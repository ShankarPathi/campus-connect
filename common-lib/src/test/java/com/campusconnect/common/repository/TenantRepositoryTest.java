package com.campusconnect.common.repository;

import com.campusconnect.common.domain.Season;
import com.campusconnect.common.domain.Tenant;
import com.campusconnect.common.domain.TenantStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRepositoryTest extends AbstractMongoIT {

    TenantRepository repository;

    @BeforeAll
    static void createIndexes() {
        ensureIndexes(Tenant.class); // builds the unique slug index from @Indexed
    }

    @BeforeEach
    void setUp() {
        repository = new TenantRepository(mongoTemplate);
        // clear documents but keep the unique slug index between tests
        mongoTemplate.remove(new Query(), Tenant.class);
    }

    private Tenant tenant(String slug) {
        Tenant t = new Tenant();
        t.setSlug(slug);
        t.setName(slug + " College");
        t.setSubdomain(slug);
        t.setBranches(List.of("CSE", "ECE"));
        t.setBatches(List.of("2026"));
        t.setSeason(new Season(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)));
        t.setStatus(TenantStatus.ACTIVE);
        return t;
    }

    @Test
    void saveAndFindBySlug() {
        Tenant saved = repository.save(tenant("vignan"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(repository.findBySlug("vignan")).isPresent();
        assertThat(repository.findById(saved.getId())).isPresent();
        assertThat(repository.existsBySlug("vignan")).isTrue();
        assertThat(repository.existsBySlug("nope")).isFalse();
    }

    @Test
    void duplicateSlug_isRejectedByUniqueIndex() {
        repository.save(tenant("vignan"));

        assertThatThrownBy(() -> repository.save(tenant("vignan")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void distinctSlugs_coexist() {
        repository.save(tenant("vignan"));
        repository.save(tenant("kits"));

        assertThat(repository.findBySlug("vignan")).isPresent();
        assertThat(repository.findBySlug("kits")).isPresent();
    }

    @Test
    void nullOrBlankSlug_isRejected() {
        assertThatThrownBy(() -> repository.save(tenant(null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.save(tenant("   "))).isInstanceOf(IllegalArgumentException.class);
    }
}
