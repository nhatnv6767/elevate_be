package com.elevatebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.DependsOn;

@SpringBootApplication
@EnableConfigurationProperties
@DependsOn("dockerConfig")
public class ElevateBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElevateBankingApplication.class, args);
    }

}
