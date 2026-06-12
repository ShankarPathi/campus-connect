package com.campusconnect.common.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * SMTP-backed {@link EmailService} (Mailpit in dev at {@code localhost:1025}, Brevo in prod). This is
 * the sole {@code EmailService} in a running service; tests inject their own {@code @Primary}
 * recording fake, which takes precedence. {@link JavaMailSender#send} connects lazily, so
 * constructing this bean opens no socket.
 *
 * <p>{@code @ConditionalOnProperty("spring.mail.host")}: this bean (and the autoconfigured
 * {@link JavaMailSender} it needs) only exists in a service that has configured mail — student-service
 * here, recruiter-service in Story 2.2. Services that send no email (admin, gateway) skip it cleanly.
 * Unlike {@code @ConditionalOnMissingBean}, a property condition is reliable on a scanned component.
 */
@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailService implements EmailService {

    private static final String SUBJECT = "Verify your Campus Connect account";
    private static final String FROM = "no-reply@campusconnect.app";

    private final JavaMailSender mailSender;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM);
        message.setTo(toEmail);
        message.setSubject(SUBJECT);
        message.setText("""
                Welcome to Campus Connect!

                Please verify your email address by opening the link below:

                %s

                This link expires in 24 hours. If you did not create an account, ignore this email.
                """.formatted(verificationLink));
        mailSender.send(message);
    }
}
