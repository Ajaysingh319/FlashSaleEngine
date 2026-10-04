package com.flashsale.catalog.document;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;
import java.util.Properties;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the (eventId, name) uniqueness that ticket-type setup retries rely on. Uses Spring Data's own
 * index resolver, i.e. exactly what auto-index-creation sends to MongoDB at startup.
 */
class TicketTypeIndexTest {

    private static IndexDefinition eventNameIndex() {
        // Configured like Spring Boot's mapping context, so java.time types are treated as simple values.
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        mappingContext.setAutoIndexCreation(true);
        List<IndexDefinition> indexes = StreamSupport
                .stream(IndexResolver.create(mappingContext).resolveIndexFor(TicketType.class).spliterator(), false)
                .map(IndexDefinition.class::cast)
                .filter(index -> "event_name_unique".equals(index.getIndexOptions().getString("name")))
                .toList();
        assertEquals(1, indexes.size(), "expected exactly one event_name_unique index");
        return indexes.get(0);
    }

    @Test
    void eventIdAndNameAreUniqueTogether() {
        IndexDefinition index = eventNameIndex();

        assertEquals(new Document("eventId", 1).append("name", 1), index.getIndexKeys());
        assertEquals(Boolean.TRUE, index.getIndexOptions().get("unique"));
    }

    /**
     * Ticket types written before this change have no eventId. Without the partial filter, a unique index
     * treats a missing eventId as null, so two legacy "VIP" ticket types of different events collide and
     * index creation (and therefore Catalog startup) fails on an existing database.
     */
    @Test
    void uniquenessAppliesOnlyToDocumentsWithEventId() {
        assertEquals(new Document("eventId", new Document("$exists", true)),
                eventNameIndex().getIndexOptions().get("partialFilterExpression"));
    }

    @Test
    void applicationConfigCreatesIndexesAtStartup() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertNotNull(properties);
        assertEquals("true", String.valueOf(properties.get("spring.data.mongodb.auto-index-creation")));
    }
}
