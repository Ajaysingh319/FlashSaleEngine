package com.flashsale.reservation.config;

import com.mongodb.ReadPreference;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Component;

/**
 * Mongo access for admin monitoring. Reads prefer replica-set secondaries, so a dashboard polling during a flash
 * sale does not compete with reservation transactions on the primary. Replication lag of a few milliseconds is
 * acceptable for monitoring; each inventory document is still read atomically.
 */
@Component
public class ReportingReads {
    private final MongoTemplate mongoTemplate;

    public ReportingReads(MongoDatabaseFactory databaseFactory, MongoConverter converter) {
        mongoTemplate = new MongoTemplate(databaseFactory, converter);
        mongoTemplate.setReadPreference(ReadPreference.secondaryPreferred());
    }

    public MongoTemplate mongo() {
        return mongoTemplate;
    }
}
