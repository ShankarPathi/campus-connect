package com.campusconnect.student.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;

/**
 * The result of a successful registration. Deliberately minimal — never the id, password hash, or
 * verification token (the token is delivered only by email).
 */
public record StudentRegistrationResponse(String email, AccountStatus accountStatus) {

    public static StudentRegistrationResponse from(User user) {
        return new StudentRegistrationResponse(user.getEmail(), user.getAccountStatus());
    }
}
