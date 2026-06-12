package com.campusconnect.common.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * Base for repository integration tests against a real MongoDB 8 (Testcontainers). The
 * {@link MongoTemplate} is built by hand — {@code common-lib} has no Spring Boot application, so we
 * avoid the test-slice machinery entirely. {@link #ensureIndexes(Class)} creates an entity's
 * {@code @Indexed} indexes from its annotations (the same definitions production builds via
 * {@code auto-index-creation}), so the unique-index behaviour is genuinely exercised.
 */
@Testcontainers
public abstract class AbstractMongoIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    protected static MongoTemplate mongoTemplate;
    private static MongoMappingContext mappingContext;

    @BeforeAll
    static void initTemplate() {
        MongoClient client = MongoClients.create(MONGO.getReplicaSetUrl());
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client, "campusconnect_test");

        // Wire the SimpleTypeHolder so java.time types (LocalDate/Instant) are treated as simple
        // values, not recursed into as entities (which would fail under JPMS).
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setAutoIndexCreation(true);
        mappingContext.afterPropertiesSet();

        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        mongoTemplate = new MongoTemplate(factory, converter);
    }

    /** Creates the entity's {@code @Indexed} indexes (resolved from its annotations). */
    protected static void ensureIndexes(Class<?> type) {
        IndexResolver resolver = IndexResolver.create(mappingContext);
        resolver.resolveIndexFor(type).forEach(index -> mongoTemplate.indexOps(type).ensureIndex(index));
    }
}
