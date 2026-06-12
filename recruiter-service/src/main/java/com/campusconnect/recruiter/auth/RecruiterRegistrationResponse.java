package com.campusconnect.recruiter.auth;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;

/** Result of a successful recruiter registration. Never the id, password hash, token, or profile. */
public record RecruiterRegistrationResponse(String email, AccountStatus accountStatus) {

    public static RecruiterRegistrationResponse from(User user) {
        return new RecruiterRegistrationResponse(user.getEmail(), user.getAccountStatus());
    }
}
