//package com.elevatebanking.config;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.InitializingBean;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.DependsOn;
//import org.springframework.stereotype.Component;
//
//import java.sql.Connection;
//import java.sql.DatabaseMetaData;
//import java.sql.DriverManager;
//import java.sql.ResultSet;
//
//@Component
//@DependsOn({"dockerConfig", "entityManagerFactory"})
//public class DatabaseSchemaInitializer implements InitializingBean {
//    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
//    @Value("${spring.datasource.url}")
//    private String dbUrl;
//
//    @Value("${spring.datasource.username}")
//    private String dbUsername;
//
//    @Value("${spring.datasource.password}")
//    private String dbPassword;
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//        Thread.sleep(5000);
//        int maxRetries = 5;
//        int retryCount = 0;
//        boolean schemaVerified = false;
//
//        while (retryCount < maxRetries && !schemaVerified) {
//            try {
//                if (!verifyDatabaseSchema()) {
//                    throw new RuntimeException("Database schema verification failed");
//                }
//                schemaVerified = true;
//                log.info("Database schema verified successfully!");
//            } catch (Exception e) {
//                retryCount++;
//                if (retryCount < maxRetries) {
//                    log.warn("Failed to verify schema, retrying... ({}/{})", retryCount, maxRetries);
//                    Thread.sleep(2000);
//                } else {
//                    throw e;
//                }
//            }
//        }
//    }
//
//    private boolean verifyDatabaseSchema() {
//        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
//            DatabaseMetaData metaData = conn.getMetaData();
//            String[] tables = {
//                    "users", "accounts", "transactions", "roles",
//                    "user_roles", "audit_logs", "beneficiaries",
//                    "bill_payments", "billers", "event_logs",
//                    "loyalty_points", "notifications", "point_transactions",
//                    "savings_accounts", "savings_products"
//            };
//
//            for (String table : tables) {
//                ResultSet rs = metaData.getTables(null, "public", table, null);
//                if (!rs.next()) {
//                    log.error("Table {} not found", table);
//                    return false;
//                }
//            }
//            return true;
//        } catch (Exception e) {
//            log.error("Error verifying schema", e);
//            return false;
//        }
//    }
//}