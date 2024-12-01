package com.elevatebanking.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Configuration
@DependsOn("databaseInitializer")
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void initializeDatabase() {
        log.info("Starting database schema initialization...");

        try {
            // Get all entity metadata
            Set<EntityType<?>> entities = entityManagerFactory.getMetamodel().getEntities();

            log.info("Found {} entities to process", entities.size());

            // Log each entity being processed
            entities.forEach(entity -> {
                log.info("Processing entity: {}", entity.getName());
            });

            log.info("Database schema initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during database schema initialization", e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

}
