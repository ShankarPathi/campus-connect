package com.campusconnect.common.domain;

import java.time.LocalDate;

/** A college's placement-season window. Embedded in {@link Tenant} (small, read together). */
public class Season {

    private LocalDate start;
    private LocalDate end;

    public Season() {
    }

    public Season(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public LocalDate getStart() {
        return start;
    }

    public void setStart(LocalDate start) {
        this.start = start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public void setEnd(LocalDate end) {
        this.end = end;
    }
}
