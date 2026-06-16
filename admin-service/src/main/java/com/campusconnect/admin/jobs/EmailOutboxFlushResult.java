package com.campusconnect.admin.jobs;

/**
 * The summary of one email-outbox-flush run (Story 8.2) — {@code sentCount} rows delivered and marked
 * {@code SENT}, and {@code failedCount} rows whose send threw (incremented + left {@code PENDING}, or
 * dead-lettered to {@code FAILED} at the attempt cap). Logged at INFO by the job.
 */
public record EmailOutboxFlushResult(int sentCount, int failedCount) {
}
