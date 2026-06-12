package com.campusconnect.common.domain;

/**
 * Embedded academic sub-document of a {@link StudentProfile} (Story 3.1). These are the fields the
 * Epic-5 eligibility engine reads: {@code branch}, {@code cgpa}, {@code activeBacklogs} (plus the
 * profile's top-level {@code batch}). {@code cgpa}/{@code activeBacklogs} are boxed so "not filled
 * yet" (null) is distinguishable from a real {@code 0} when computing completion.
 */
public class AcademicDetails {

    private String branch;
    private Double cgpa;
    private Integer activeBacklogs;

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Double getCgpa() {
        return cgpa;
    }

    public void setCgpa(Double cgpa) {
        this.cgpa = cgpa;
    }

    public Integer getActiveBacklogs() {
        return activeBacklogs;
    }

    public void setActiveBacklogs(Integer activeBacklogs) {
        this.activeBacklogs = activeBacklogs;
    }
}
