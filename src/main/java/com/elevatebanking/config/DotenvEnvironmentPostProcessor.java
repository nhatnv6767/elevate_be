package com.elevatebanking.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.load();
        Map<String, Object> dotenvMap = dotenv.entries()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, dotenvMap));
    }

}
