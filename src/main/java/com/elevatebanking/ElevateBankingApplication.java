package com.elevatebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.DependsOn;

import com.elevatebanking.config.DockerConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;

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
@EnableConfigurationProperties
public class ElevateBankingApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ElevateBankingApplication.class);
        app.setRegisterShutdownHook(true);
        app.run(args);
    }
}
