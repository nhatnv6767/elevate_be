package com.elevatebanking.config;

import com.elevatebanking.service.nonImp.GoogleTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class EmailConfig {
    private final GoogleTokenService tokenService;

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    // @Value("${spring.mail.password}")
    @Value("${MAIL_ELEVATE_BANKING_PASSWORD}")
    private String password;

    @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID}")
    private String clientId;

    @Value("${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${MAIL_OAUTH2_SCOPE}")
    private String scope;

    @Value("${MAIL_OAUTH2_ACCESS_TOKEN}")
    private String accessToken;

    @Bean
    public JavaMailSender javaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        // Thêm các thuộc tính SASL và OAuth2
        props.put("mail.smtp.sasl.enable", "true");
        props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.auth.login.disable", "true");
        props.put("mail.smtp.auth.plain.disable", "true");
        props.put("mail.smtp.oauth2.disable", "false");

        // Lấy access token mới
        String newAccessToken = tokenService.refreshAccessToken();
        props.put("mail.smtp.oauth2.access.token", newAccessToken);

        // Debug mode
        props.put("mail.debug", "true");

        return mailSender;

    }
}
