package com.campusconnect.common.repository;

import com.campusconnect.common.domain.TenantAwareDocument;
import com.campusconnect.common.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAwareRepositoryTest extends AbstractMongoIT {

    TestDocRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TestDocRepository(mongoTemplate);
        mongoTemplate.remove(new Query(), TestDoc.class); // unscoped cleanup between tests
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void save_stampsTenantIdFromContextWhenAbsent() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        TestDoc saved = repository.save(doc("hello"));

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_alwaysStampsContextTenant_ignoringForgedTenantId() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        TestDoc d = doc("hello");
        d.setTenantId("tenant-x"); // forged — must be ignored

        repository.save(d);

        assertThat(d.getTenantId()).isEqualTo("tenant-a"); // stamped to context, not trusted
    }

    @Test
    void save_cannotOverwriteAnotherTenantsDocumentById() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        String id = repository.save(doc("a-data")).getId();

        // tenant-b crafts an entity carrying A's id
        TenantContext.set("tenant-b", "u2", "STUDENT");
        TestDoc forged = doc("hacked");
        forged.setId(id);
        assertThatThrownBy(() -> repository.save(forged)).isInstanceOf(IllegalStateException.class);

        // A's document is untouched
        TenantContext.set("tenant-a", "u1", "STUDENT");
        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.findById(id).orElseThrow().getValue()).isEqualTo("a-data");
    }

    @Test
    void save_updatesOwnDocument() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        TestDoc d = repository.save(doc("v1"));
        d.setValue("v2");

        repository.save(d);

        assertThat(repository.findById(d.getId()).orElseThrow().getValue()).isEqualTo("v2");
    }

    @Test
    void findAll_andCount_returnOnlyCurrentTenantSubset() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        repository.save(doc("a1"));
        repository.save(doc("a2"));
        TenantContext.set("tenant-b", "u2", "STUDENT");
        repository.save(doc("b1"));

        assertThat(repository.findAll()).extracting(TestDoc::getValue).containsExactlyInAnyOrder("b1");
        assertThat(repository.count()).isEqualTo(1);

        TenantContext.set("tenant-a", "u1", "STUDENT");
        assertThat(repository.findAll()).extracting(TestDoc::getValue).containsExactlyInAnyOrder("a1", "a2");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void find_withCustomQuery_isTenantScoped() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        repository.save(doc("shared-value"));
        TenantContext.set("tenant-b", "u2", "STUDENT");
        repository.save(doc("shared-value")); // same value, different tenant

        List<TestDoc> bResults = repository.find(new Query(Criteria.where("value").is("shared-value")));

        assertThat(bResults).hasSize(1);
        assertThat(bResults.get(0).getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    void reads_areIsolatedAcrossTenants() {
        // write under tenant A
        TenantContext.set("tenant-a", "u1", "STUDENT");
        TestDoc saved = repository.save(doc("a-data"));
        String id = saved.getId();
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.count()).isEqualTo(1);

        // switch to tenant B — A's data is invisible
        TenantContext.set("tenant-b", "u2", "STUDENT");
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsById(id)).isFalse();
        assertThat(repository.count()).isZero();

        // back to A — visible again
        TenantContext.set("tenant-a", "u1", "STUDENT");
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    void deleteById_isTenantScoped() {
        TenantContext.set("tenant-a", "u1", "STUDENT");
        String id = repository.save(doc("a-data")).getId();

        // tenant B cannot delete A's document
        TenantContext.set("tenant-b", "u2", "STUDENT");
        repository.deleteById(id);

        TenantContext.set("tenant-a", "u1", "STUDENT");
        assertThat(repository.findById(id)).isPresent(); // still there
    }

    @Test
    void operations_failWithoutBoundTenant() {
        TenantContext.clear();
        assertThatThrownBy(() -> repository.findAll()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.save(doc("x"))).isInstanceOf(IllegalStateException.class);
    }

    private TestDoc doc(String value) {
        TestDoc d = new TestDoc();
        d.setValue(value);
        return d;
    }

    // ── test entity + concrete repository ──

    @Document("test_tenant_docs")
    static class TestDoc extends TenantAwareDocument {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static class TestDocRepository extends TenantAwareRepository<TestDoc> {
        TestDocRepository(MongoTemplate mongoTemplate) {
            super(mongoTemplate, TestDoc.class);
        }
    }
}
