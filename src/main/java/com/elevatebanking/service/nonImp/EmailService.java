package com.elevatebanking.service.nonImp;

import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.exception.EmailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;

import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    @Value("${MAIL_USERNAME}")
    private String fromEmail;

    // @Retryable(
    // exceptionExpression = "#{@emailSendException}",
    // maxAttempts = 3,
    // backoff = @Backoff(delay = 1000)
    // )
    // public void sendResetPasswordEmail(String toEmail, String token, String
    // username) {
    // try {
    // MimeMessage message = mailSender.createMimeMessage();
    // MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    //
    // Context context = new Context();
    // context.setVariable("username", username);
    // context.setVariable("resetLink",
    // "http://localhost:8080/api/v1/auth/reset-password?token=" + token);
    //
    // String htmlContent = templateEngine.process("email/reset-password", context);
    //
    // helper.setFrom(fromEmail);
    // helper.setTo(toEmail);
    // helper.setSubject("Reset Your Password - Elevate Banking");
    // helper.setText(htmlContent, true);
    //
    // mailSender.send(message);
    //
    // } catch (Exception e) {
    // log.error("Failed to send reset password email", e);
    // throw new EmailSendException("Failed to send reset password email", e);
    // }
    // }

    // @Retryable(
    // exceptionExpression = "#{@emailSendException}",
    // maxAttempts = 3,
    // backoff = @Backoff(delay = 1000)
    // )
    // public void sendTransactionEmail(String toEmail, String subject, String
    // content) {
    // try {
    // log.info("Sending transaction email to {}", toEmail);
    //
    // MimeMessage message = mailSender.createMimeMessage();
    // MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    //
    // Context context = new Context();
    // context.setVariable("title", subject);
    // context.setVariable("message", content);
    // context.setVariable("bankName", "Elevate Banking");
    // context.setVariable("supportEmail", fromEmail);
    //
    // // Process the email template
    // String emailContent =
    // templateEngine.process("email/transaction-notification", context);
    //
    // // set email properties
    // helper.setFrom(fromEmail);
    // helper.setTo(toEmail);
    // helper.setSubject(subject);
    // helper.setText(emailContent, true);// true indicates html
    //
    // // send email
    // mailSender.send(message);
    // log.info("Transaction email sent to {}", toEmail);
    // EmailEvent event = EmailEvent.builder()
    // .to(toEmail)
    // .subject(subject)
    // .content(content)
    // .build();
    // kafkaTemplate.send("elevate.emails", event);
    // } catch (Exception e) {
    // log.error("Failed to send transaction email {} - {}", toEmail,
    // e.getMessage());
    // throw new EmailSendException("Failed to send transaction email", e);
    // }
    // }

    // ----------------------------------------------------------------

    private void processAndSendEmail(String toEmail, String subject, String content, String templateName) {
        try {
            log.info("Sending email to {}", toEmail);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String emailContent = prepareEmailContent(subject, content, templateName);
            configureAndSendEmail(helper, toEmail, subject, emailContent);
            publishEmailEvent(toEmail, subject, content);
            log.info("Email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {} - {}", toEmail, e.getMessage());
            throw new EmailSendException("Failed to send email", e);
        }
    }

    private String prepareEmailContent(String subject, String content, String templateName) {
        Context context = new Context();
        if (templateName.equals("email/reset-password")) {
            log.debug("Preparing reset password email content. Username: {}", context.getVariable("username"));

            String[] parts = content.split("\\|");
            if (parts.length >= 2) {
                String username = parts[0];
                String resetLink = "http://localhost:8080/api/v1/auth/reset-password?token=" + parts[1];
                context.setVariable("username", username);
                context.setVariable("resetLink", resetLink);

                log.debug("Reset link: {}", context.getVariable("resetLink"));
            }
        }
        context.setVariable("title", subject);
        context.setVariable("message", content);
        context.setVariable("bankName", "Elevate Banking");
        context.setVariable("supportEmail", fromEmail);
        String processedContent = templateEngine.process(templateName, context);
        log.debug("Processed email content: {}", processedContent);
        return processedContent;
    }

    private void configureAndSendEmail(MimeMessageHelper helper, String toEmail, String subject, String content)
            throws MessagingException {
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(content, true);
        mailSender.send(helper.getMimeMessage());
    }

    private void publishEmailEvent(String toEmail, String subject, String content) {
        EmailEvent event = EmailEvent.builder()
                .to(toEmail)
                .subject(subject)
                .content(content)
                .build();
        kafkaTemplate.send("elevate.emails", event);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendTransactionEmail(String toEmail, String subject, String content) {
        processAndSendEmail(toEmail, subject, content, "email/transaction-notification");
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendResetPasswordEmail(String toEmail, String token, String username) {
        String content = username + "|" + token;
        processAndSendEmail(toEmail, "Reset Your Password - Elevate Banking", content, "email/reset-password");
    }

}
