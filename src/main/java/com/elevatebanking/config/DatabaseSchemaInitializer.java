package com.elevatebanking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@DependsOn("databaseInitializer")
public class DatabaseSchemaInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUsername;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Override
    public void afterPropertiesSet() throws Exception {
        int attempts = 0;
        int maxAttempts = 30;
        while (attempts < maxAttempts) {
            try {
                if (verifyDatabaseSchema()) {
                    log.info("Database schema verified successfully");
                    return;
                }
                attempts++;
                log.info("Attempt {}/{} to verify database schema", attempts, maxAttempts);
                Thread.sleep(2000);
            } catch (Exception e) {
                log.error("Error verifying database schema on attempt {}: {}", attempts, e.getMessage(), e);
                attempts++;
                if (attempts == maxAttempts) {
                    throw new RuntimeException("Failed to verify database schema after " + maxAttempts + " attempts", e);
                }
                Thread.sleep(2000);
            }
        }
        throw new RuntimeException("Failed to verify database schema after " + maxAttempts + " attempts");
    }

    private boolean verifyDatabaseSchema() {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            // Check if database exists and is accessible
            if (!conn.isValid(5)) {
                log.error("Database connection is not valid");
                return false;
            }

            // Check if essential tables exist
            DatabaseMetaData metaData = conn.getMetaData();
            String[] tables = {"users", "accounts", "transactions", "roles"};
            
            for (String table : tables) {
                try (ResultSet rs = metaData.getTables(null, null, table, new String[]{"TABLE"})) {
                    if (!rs.next()) {
                        log.warn("Table '{}' not found", table);
                        return false;
                    }
                    log.info("Table '{}' exists", table);
                }
            }
            
            log.info("All required tables exist");
            return true;
        } catch (Exception e) {
            log.error("Error verifying database schema: {}", e.getMessage(), e);
            return false;
        }
    }
}