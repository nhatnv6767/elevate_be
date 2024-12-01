package com.elevatebanking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

// @Component
// @DependsOn({"databaseInitializationConfig"})
// @Order(Ordered.HIGHEST_PRECEDENCE + 2)
// public class DatabaseSchemaInitializer implements InitializingBean {
    // private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    // @Value("${spring.datasource.url}")
    // private String dbUrl;

    // @Value("${spring.datasource.username}")
    // private String dbUsername;

    // @Value("${spring.datasource.password}")
    // private String dbPassword;

    // @Override
    // public void afterPropertiesSet() throws Exception {
    //     Thread.sleep(5000); // Đợi schema được tạo xong

    //     if (!verifyDatabaseSchema()) {
    //         throw new RuntimeException("Database schema verification failed");
    //     }

    //    log.info("Database schema verified successfully!");
    // }

    // private boolean verifyDatabaseSchema() {
    //     try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
    //         DatabaseMetaData metaData = conn.getMetaData();
    //         String[] tables = {
    //             "users", "accounts", "transactions", "roles", 
    //             "user_roles", "audit_logs", "beneficiaries",
    //             "bill_payments", "billers", "event_logs",
    //             "loyalty_points", "notifications", "point_transactions",
    //             "savings_accounts", "savings_products"
    //         };

    //         for (String table : tables) {
    //             ResultSet rs = metaData.getTables(null, "public", table.toLowerCase(), new String[]{"TABLE"});
    //             if (!rs.next()) {
    //                 log.error("Table '{}' not found", table);
    //                 return false;
    //             }
    //             log.info("Table '{}' exists", table);
    //             rs.close();
    //         }
    //         return true;
    //     } catch (Exception e) {
    //         log.error("Error verifying schema", e);
    //         return false;
    //     }
    // }
// }