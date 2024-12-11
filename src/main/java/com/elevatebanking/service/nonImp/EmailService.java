package com.elevatebanking.service.nonImp;

import com.elevatebanking.exception.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final GoogleTokenService tokenService;

    @Value("${MAIL_USERNAME}")
    private String fromEmail;

    @Value("${MAIL_OAUTH2_ACCESS_TOKEN}")
    private String accessToken;

    public void sendResetPasswordEmail(String toEmail, String token) {
        int maxRetries = 2;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                sendEmail(toEmail, token);
                return;
            } catch (MailAuthenticationException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new EmailSendException("Failed to send email after " + maxRetries + " attempts", e);
                }

                String newAccessToken = tokenService.refreshAccessToken();
                updateMailSenderToken(newAccessToken);
                log.info("Retrying email send with new access token, attempt {}", attempt);
            }
        }
    }

    private void sendEmail(String toEmail, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - Elevate Banking");
            helper.setText(createEmailTemplate("http://localhost:8080/reset-password?token=" + token), true);

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send reset password email", e);
            throw new EmailSendException("Failed to send reset password email", e);
        }
    }

    private void updateMailSenderToken(String newAccessToken) {
        JavaMailSenderImpl mailSenderImpl = (JavaMailSenderImpl) mailSender;
        Properties props = mailSenderImpl.getJavaMailProperties();
        props.put("mail.smtp.oauth2.access.token", newAccessToken);
        props.put("mail.smtp.sasl.enable", "true");
        props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
        props.put("mail.smtp.auth.oauth2.disable", "false");
    }

    private String createEmailTemplate(String resetLink) {
        return String.format(
                """
                            <div style="font-family: Arial, sans-serif; margin: 0 auto; max-width: 600px; padding: 20px;">
                                <h2>Reset Your Password</h2>
                                <p>A password reset was requested for your account. If this was you, click the link below to reset your password:</p>
                                <p style="margin: 20px 0;">
                                    <a href="%s" style="background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">
                                        Reset Password
                                    </a>
                                </p>
                                <p>This link will expire in 30 minutes.</p>
                                <p>If you didn't request this, please ignore this email.</p>
                                <hr>
                                <p style="color: #666; font-size: 12px;">
                                    This is an automated message from Elevate Banking. Please do not reply.
                                </p>
                            </div>
                        """,
                resetLink);
    }

}
