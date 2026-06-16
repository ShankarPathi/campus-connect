package com.campusconnect.admin.jobs;

import com.campusconnect.common.domain.EmailOutbox;
import com.campusconnect.common.domain.EmailStatus;
import com.campusconnect.common.email.EmailService;
import com.campusconnect.common.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailOutboxFlushService} (Story 8.2) — the send / retry / dead-letter state machine and
 * per-row isolation, with a mocked {@link EmailOutboxRepository} + {@link EmailService} (no Mongo/SMTP). The
 * real {@code findSendable} index filter (a {@code SENT}/over-cap row is never re-picked) is covered by the
 * integration test.
 */
class EmailOutboxFlushServiceTest {

    private final EmailOutboxRepository repo = mock(EmailOutboxRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final EmailOutboxFlushService service = new EmailOutboxFlushService(repo, emailService);

    private static EmailOutbox row(String id, int attempts) {
        EmailOutbox r = new EmailOutbox();
        r.setId(id);
        r.setTenantId("t1");
        r.setToEmail(id + "@college.edu");
        r.setSubject("Subj " + id);
        r.setBody("Body " + id);
        r.setEventId("E:" + id);
        r.setUserId("u-" + id);
        r.setStatus(EmailStatus.PENDING);
        r.setAttempts(attempts);
        return r;
    }

    @Test
    void flush_sendsPendingRow_marksSent() {
        EmailOutbox r = row("a", 0);
        when(repo.findSendable(EmailOutboxFlushService.MAX_ATTEMPTS, EmailOutboxFlushService.BATCH_SIZE)).thenReturn(List.of(r));
        doNothing().when(emailService).sendEmail(any(), any(), any());

        EmailOutboxFlushResult result = service.flush();

        verify(emailService).sendEmail(eq("a@college.edu"), eq("Subj a"), eq("Body a"));
        assertThat(r.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(r.getSentAt()).isNotNull();
        verify(repo).save(r);
        assertThat(result).isEqualTo(new EmailOutboxFlushResult(1, 0));
    }

    @Test
    void flush_sendFails_incrementsAttempts_staysPendingUnderCap() {
        EmailOutbox r = row("b", 1);
        when(repo.findSendable(EmailOutboxFlushService.MAX_ATTEMPTS, EmailOutboxFlushService.BATCH_SIZE)).thenReturn(List.of(r));
        doThrow(new RuntimeException("smtp down")).when(emailService).sendEmail(any(), any(), any());

        EmailOutboxFlushResult result = service.flush();

        assertThat(r.getAttempts()).isEqualTo(2);
        assertThat(r.getStatus()).isEqualTo(EmailStatus.PENDING); // still retryable
        assertThat(r.getLastError()).contains("smtp down");
        assertThat(r.getSentAt()).isNull();
        verify(repo).save(r);
        assertThat(result).isEqualTo(new EmailOutboxFlushResult(0, 1));
    }

    @Test
    void flush_sendFailsAtCap_deadLettersToFailed() {
        EmailOutbox r = row("c", EmailOutboxFlushService.MAX_ATTEMPTS - 1); // one attempt left
        when(repo.findSendable(EmailOutboxFlushService.MAX_ATTEMPTS, EmailOutboxFlushService.BATCH_SIZE)).thenReturn(List.of(r));
        doThrow(new RuntimeException("still down")).when(emailService).sendEmail(any(), any(), any());

        service.flush();

        assertThat(r.getAttempts()).isEqualTo(EmailOutboxFlushService.MAX_ATTEMPTS);
        assertThat(r.getStatus()).isEqualTo(EmailStatus.FAILED); // dead-lettered
    }

    @Test
    void flush_oneBadRow_doesNotAbortTheBatch() {
        EmailOutbox a = row("a", 0);
        EmailOutbox b = row("b", 0);
        EmailOutbox c = row("c", 0);
        when(repo.findSendable(EmailOutboxFlushService.MAX_ATTEMPTS, EmailOutboxFlushService.BATCH_SIZE)).thenReturn(List.of(a, b, c));
        // middle row's send throws; the other two succeed
        doNothing().doThrow(new RuntimeException("blip")).doNothing()
                .when(emailService).sendEmail(any(), any(), any());

        EmailOutboxFlushResult result = service.flush();

        verify(emailService, times(3)).sendEmail(any(), any(), any()); // all attempted
        assertThat(result).isEqualTo(new EmailOutboxFlushResult(2, 1));
        assertThat(a.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(b.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(c.getStatus()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void flush_emptyOutbox_isNoOp() {
        when(repo.findSendable(EmailOutboxFlushService.MAX_ATTEMPTS, EmailOutboxFlushService.BATCH_SIZE)).thenReturn(List.of());

        EmailOutboxFlushResult result = service.flush();

        assertThat(result).isEqualTo(new EmailOutboxFlushResult(0, 0));
        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(repo, never()).save(any());
    }
}
