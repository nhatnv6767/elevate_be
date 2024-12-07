package com.elevatebanking.service.nonImp;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public void sendPasswordResetEmail(String to, String token) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("google", "user");
        // Sử dụng client để thiết lập các thông tin xác thực nếu cần

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText(String.format(
                "To reset your password, click the link below:\nhttps://yourapp.com/reset-password?token=%s\n\n" +
                        "If you did not request a password reset, please ignore this email.",
                token));
        mailSender.send(message);
    }
}
