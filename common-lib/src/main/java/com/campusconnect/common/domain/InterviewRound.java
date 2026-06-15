package com.campusconnect.common.domain;

import java.time.Instant;

/**
 * One round in a drive's interview sequence (Story 6.3, FR-20) — embedded in {@link Drive}'s {@code rounds[]}
 * (the same embed rationale as {@link EligibilityCriteria}: small, bounded, read with the drive). This is the
 * single source of a round's definition; the per-student instances ({@link ApplicationRound}) reference it by
 * {@code (driveId, roundOrder)} and carry only the per-student {@code result} — so a reschedule mutates here
 * alone. {@code roundOrder} is 1-based and positional ("round N+1 follows round N").
 */
public class InterviewRound {

    private int roundOrder;
    private String name;
    private InterviewMode mode;
    private Instant schedule;
    private String venueOrLink;

    public int getRoundOrder() {
        return roundOrder;
    }

    public void setRoundOrder(int roundOrder) {
        this.roundOrder = roundOrder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public void setMode(InterviewMode mode) {
        this.mode = mode;
    }

    public Instant getSchedule() {
        return schedule;
    }

    public void setSchedule(Instant schedule) {
        this.schedule = schedule;
    }

    public String getVenueOrLink() {
        return venueOrLink;
    }

    public void setVenueOrLink(String venueOrLink) {
        this.venueOrLink = venueOrLink;
    }
}
