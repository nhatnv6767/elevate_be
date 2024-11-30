package com.elevatebanking.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("databaseInitializer")
public class DatabaseSchemaInitializer implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        // Wait additional time after container is ready
        Thread.sleep(5000);
    }
}
