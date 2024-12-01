package com.elevatebanking;

import com.elevatebanking.config.DatabaseInitializer;
//import com.elevatebanking.config.DatabaseSchemaInitializer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
public class ElevateBankingApplication {
    public static void main(String[] args) {

        SpringApplication application = new SpringApplication(ElevateBankingApplication.class);

        // Add wait for services
        application.addInitializers((ApplicationContextInitializer<GenericApplicationContext>) ctx -> {
            ctx.registerBean("databaseInitializer", DatabaseInitializer.class);
        });

        application.run(args);
    }
}
