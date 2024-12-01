package com.elevatebanking.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.sql.Statement;
import java.util.EnumSet;

@Configuration
@DependsOn("entityManagerFactory")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void initializeSchema() {
        log.info("Starting schema initialization...");

        try {
            // Lấy Hibernate session
            Session session = entityManagerFactory.createEntityManager().unwrap(Session.class);
            session.doWork(connection -> {
                // Tạo schema trực tiếp bằng SQL
                try (Statement stmt = connection.createStatement()) {
                    // Xóa schema nếu tồn tại
                    stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
                    // Tạo lại schema
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS public");
                }
            });

            // Tạo metadata để sinh DDL
            MetadataSources metadata = new MetadataSources(
                    ((SessionFactoryImpl) entityManagerFactory.unwrap(SessionFactory.class))
                            .getServiceRegistry()
            );

            // Thêm tất cả entities
            for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
                metadata.addAnnotatedClass(entity.getJavaType());
            }

            // Tạo schema
            SchemaExport schemaExport = new SchemaExport();
            schemaExport.create(EnumSet.of(TargetType.DATABASE), metadata.buildMetadata());

            log.info("Schema initialization completed successfully!");
        } catch (Exception e) {
            log.error("Error initializing schema", e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

}
