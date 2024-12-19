package com.elevatebanking.service.nonImp;

import com.elevatebanking.exception.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;

import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
//    private final GoogleTokenService tokenService;

    @Value("${MAIL_USERNAME}")
    private String fromEmail;

//    @Value("${MAIL_OAUTH2_ACCESS_TOKEN}")
//    private String accessToken;

    public void sendResetPasswordEmail(String toEmail, String token, String username) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("resetLink", "http://localhost:8080/api/v1/auth/reset-password?token=" + token);

            String htmlContent = templateEngine.process("email/reset-password", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - Elevate Banking");
            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (Exception e) {
            log.error("Failed to send reset password email", e);
            throw new EmailSendException("Failed to send reset password email", e);
        }
    }

    public void sendTransactionEmail(String toEmail, String subject, String content) {
        try {
            log.info("Sending transaction email to {}", toEmail);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("title", subject);
            context.setVariable("message", content);
            context.setVariable("bankName", "Elevate Banking");
            context.setVariable("supportEmail", fromEmail);

            // Process the email template
            String emailContent = templateEngine.process("email/transaction-notification", context);

            // set email properties
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(emailContent, true);// true indicates html

            // send email
            mailSender.send(message);
            log.info("Transaction email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send transaction email {} - {}", toEmail, e.getMessage());
            throw new EmailSendException("Failed to send transaction email", e);
        }
    }

//    private void sendEmail(String toEmail, String token) {
//        try {
//            MimeMessage message = mailSender.createMimeMessage();
//            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
//
//            helper.setFrom(fromEmail);
//            helper.setTo(toEmail);
//            helper.setSubject("Reset Your Password - Elevate Banking");
//            helper.setText(createEmailTemplate("http://localhost:8080/reset-password?token=" + token), true);
//
//            mailSender.send(message);
//        } catch (Exception e) {
//            log.error("Failed to send reset password email", e);
//            throw new EmailSendException("Failed to send reset password email", e);
//        }
//    }
//
//    private void updateMailSenderToken(String newAccessToken) {
//        JavaMailSenderImpl mailSenderImpl = (JavaMailSenderImpl) mailSender;
//        Properties props = mailSenderImpl.getJavaMailProperties();
//        props.put("mail.smtp.oauth2.access.token", newAccessToken);
//        props.put("mail.smtp.sasl.enable", "true");
//        props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
//        props.put("mail.smtp.auth.oauth2.disable", "false");
//    }
//
//    private String createEmailTemplate(String resetLink) {
//        return String.format(
//                """
//                            <div style="font-family: Arial, sans-serif; margin: 0 auto; max-width: 600px; padding: 20px;">
//                                <h2>Reset Your Password</h2>
//                                <p>A password reset was requested for your account. If this was you, click the link below to reset your password:</p>
//                                <p style="margin: 20px 0;">
//                                    <a href="%s" style="background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">
//                                        Reset Password
//                                    </a>
//                                </p>
//                                <p>This link will expire in 30 minutes.</p>
//                                <p>If you didn't request this, please ignore this email.</p>
//                                <hr>
//                                <p style="color: #666; font-size: 12px;">
//                                    This is an automated message from Elevate Banking. Please do not reply.
//                                </p>
//                            </div>
//                        """,
//                resetLink);
//    }

}
