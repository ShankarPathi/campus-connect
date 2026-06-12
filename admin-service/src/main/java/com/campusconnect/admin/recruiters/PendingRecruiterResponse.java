package com.campusconnect.admin.recruiters;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.RecruiterProfile;
import com.campusconnect.common.domain.User;

/** A recruiter awaiting (or having received) an approval decision, with the company details the TPO vets. */
public record PendingRecruiterResponse(
        String userId,
        String email,
        AccountStatus accountStatus,
        String companyName,
        String companyWebsite,
        String industry,
        String companyDescription,
        String recruiterDesignation,
        String contactPhone) {

    public static PendingRecruiterResponse of(User user, RecruiterProfile profile) {
        return new PendingRecruiterResponse(
                user.getId(),
                user.getEmail(),
                user.getAccountStatus(),
                profile == null ? null : profile.getCompanyName(),
                profile == null ? null : profile.getCompanyWebsite(),
                profile == null ? null : profile.getIndustry(),
                profile == null ? null : profile.getCompanyDescription(),
                profile == null ? null : profile.getRecruiterDesignation(),
                profile == null ? null : profile.getContactPhone());
    }
}
