package com.elevatebanking.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@DependsOn("entityManagerFactory")
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void initializeSchema() {
        log.info("Starting schema initialization...");
        try {
            // Kiểm tra kết nối database
            var em = entityManagerFactory.createEntityManager();
            try {
//                em.getTransaction().begin();
                // Thực hiện một query đơn giản để test kết nối
                em.createNativeQuery("SELECT 1").getSingleResult();
                // em.getTransaction().commit();
                log.info("Database connection verified and schema initialization completed");
            } finally {
                if (em != null && em.isOpen()) {
                    em.close();
                }
            }
        } catch (Exception e) {
            log.error("Error verifying database connection", e);
            throw new RuntimeException("Failed to verify database connection", e);
        }
    }

}
