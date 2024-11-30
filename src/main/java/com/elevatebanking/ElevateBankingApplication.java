package com.elevatebanking;

import com.elevatebanking.config.DatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.DependsOn;

import com.elevatebanking.config.DockerConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.support.GenericApplicationContext;

//@SpringBootApplication
//@EnableConfigurationProperties
//@DependsOn({"dockerConfig"})
//public class ElevateBankingApplication {
//
//    @Bean
//    public ApplicationRunner initializationRunner(DockerConfig dockerConfig) {
//        return args -> {
//            // Đảm bảo DockerConfig được khởi tạo trước
//        };
//    }
//
//    public static void main(String[] args) {
//        SpringApplication.run(ElevateBankingApplication.class, args);
//    }
//
//}

@SpringBootApplication
public class ElevateBankingApplication {
    public static void main(String[] args) {
//        SpringApplication app = new SpringApplication(ElevateBankingApplication.class);
//        app.setRegisterShutdownHook(true);
//
//        ConfigurableApplicationContext context = app.run(args);
//
//        // Ensure Docker services are initialized first
//        context.getBean(DockerConfig.class);

        SpringApplication application = new SpringApplication(ElevateBankingApplication.class);

        // Add wait for services
        application.addInitializers((ApplicationContextInitializer<GenericApplicationContext>) ctx -> {
            ctx.registerBean("databaseInitializer", DatabaseInitializer.class);
        });

        application.run(args);
    }
}
