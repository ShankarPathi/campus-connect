package com.campusconnect.common.domain;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Common base for all persisted documents: an id and creation/update timestamps. Timestamps are set
 * by the repositories (no auditing annotations) so {@code common-lib} needs no Spring config.
 */
public abstract class BaseDocument {

    @Id
    private String id;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
