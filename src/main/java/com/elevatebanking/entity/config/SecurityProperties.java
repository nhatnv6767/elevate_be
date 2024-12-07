package com.elevatebanking.entity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.security.admin")
@Component
public class SecurityProperties {
    // Getters and Setters
    private String username;
    private String password;
    private String roles;

}
