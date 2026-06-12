package com.campusconnect.common.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedded placement sub-document of a {@link StudentProfile} (Story 3.1). At least one {@code skills}
 * entry is required for completion; {@code expectedRole}/{@code about} are optional.
 */
public class PlacementDetails {

    private List<String> skills = new ArrayList<>();
    private String expectedRole;
    private String about;

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getExpectedRole() {
        return expectedRole;
    }

    public void setExpectedRole(String expectedRole) {
        this.expectedRole = expectedRole;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }
}
