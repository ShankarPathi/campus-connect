package com.campusconnect.common.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedded eligibility criteria of a {@link Drive} (Story 4.1, FR-10) — read read-together with the
 * drive, so embedded (architecture §5). These are the fields the Epic-5 eligibility engine matches
 * against a {@link StudentProfile}: {@code branches} (rule 7), {@code minCgpa} (rule 8),
 * {@code backlogPolicy} (rule 9), {@code batch} (rule 6). All nullable — a {@code DRAFT} drive may be
 * partially filled; the "all required" gate is submission (Story 4.2).
 */
public class EligibilityCriteria {

    private List<String> branches = new ArrayList<>();
    private Double minCgpa;
    private BacklogPolicy backlogPolicy;
    private String batch;

    public List<String> getBranches() {
        return branches;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public Double getMinCgpa() {
        return minCgpa;
    }

    public void setMinCgpa(Double minCgpa) {
        this.minCgpa = minCgpa;
    }

    public BacklogPolicy getBacklogPolicy() {
        return backlogPolicy;
    }

    public void setBacklogPolicy(BacklogPolicy backlogPolicy) {
        this.backlogPolicy = backlogPolicy;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }
}
