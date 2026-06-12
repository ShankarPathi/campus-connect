package com.campusconnect.common.email;

/**
 * Sends transactional emails. The default {@link SmtpEmailService} talks to SMTP (Mailpit in dev,
 * Brevo in prod); tests substitute a recording fake. Story 2.1 needs only verification emails;
 * later stories may widen this interface.
 */
public interface EmailService {

    /**
     * Send an account-verification email containing the clickable {@code verificationLink}.
     *
     * @param toEmail          the (already normalized) recipient address
     * @param verificationLink the absolute link that activates the account when opened
     */
    void sendVerificationEmail(String toEmail, String verificationLink);

    /**
     * Send a plain-text transactional email. The generic primitive (Story 2.2 approval/rejection
     * notices use it); Epic 8 later routes this through the async outbox.
     */
    void sendEmail(String to, String subject, String body);
}
