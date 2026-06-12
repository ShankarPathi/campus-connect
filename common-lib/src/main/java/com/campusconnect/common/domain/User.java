package com.campusconnect.common.domain;

import com.campusconnect.common.security.Role;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A platform user. Tenant-scoped: college users carry the {@code tenantId} of their college
 * (platform-admin rows carry none). Email is unique <b>per tenant</b> — the same person can be a
 * user at more than one college.
 */
@Document("users")
@CompoundIndex(name = "uniq_tenant_email", def = "{'tenantId': 1, 'email': 1}", unique = true)
public class User extends TenantAwareDocument {

    private String email;
    private String passwordHash;
    private Role role;
    private AccountStatus accountStatus;
    /** Set only when a COLLEGE_ADMIN rejects a recruiter (Story 2.2); null otherwise. */
    private String rejectionReason;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
