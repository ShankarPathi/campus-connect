package com.campusconnect.student.platform;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.security.Role;

/** The created College Admin — never carries the password or its hash. */
public record CollegeAdminResponse(
        String id,
        String tenantId,
        String email,
        Role role,
        AccountStatus accountStatus) {

    public static CollegeAdminResponse from(User user) {
        return new CollegeAdminResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus());
    }
}
