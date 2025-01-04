package com.elevatebanking;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
public class ElevateBankingApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ElevateBankingApplication.class);
        application.run(args);
    }
}
