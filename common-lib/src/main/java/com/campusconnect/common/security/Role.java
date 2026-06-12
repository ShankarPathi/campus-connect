package com.campusconnect.common.security;

/**
 * The four roles, in descending privilege: {@code PLATFORM_ADMIN > COLLEGE_ADMIN > RECRUITER > STUDENT}.
 * Used as the JWT {@code role} claim value and to build the Spring authority {@code ROLE_<name>}.
 */
public enum Role {
    PLATFORM_ADMIN,
    COLLEGE_ADMIN,
    RECRUITER,
    STUDENT;

    /** Spring Security authority form, e.g. {@code ROLE_STUDENT}. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Admin roles get the shorter access-token lifetime (architecture §11). */
    public boolean isAdmin() {
        return this == PLATFORM_ADMIN || this == COLLEGE_ADMIN;
    }
}
