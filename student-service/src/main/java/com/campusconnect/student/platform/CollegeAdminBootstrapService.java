package com.campusconnect.student.platform;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.exception.DuplicateResourceException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.TenantRepository;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.security.Role;
import com.campusconnect.common.web.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Bootstraps a tenant's College Admin (TPO) accounts (FR-2). Platform-level — not tenant-scoped. */
@Service
public class CollegeAdminBootstrapService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CollegeAdminBootstrapService(TenantRepository tenantRepository, UserRepository userRepository,
                                        PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CollegeAdminResponse bootstrap(String tenantId, CreateCollegeAdminRequest request) {
        // Normalize so per-tenant uniqueness is case-insensitive (and the unique index can't be bypassed
        // with mixed case / surrounding whitespace).
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 404 before 409: the tenant must exist before we check its users.
        if (tenantRepository.findById(tenantId).isEmpty()) {
            throw new ResourceNotFoundException("Tenant not found: " + tenantId);
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new DuplicateResourceException(
                    ErrorCode.EMAIL_ALREADY_EXISTS, "Email already in use for this tenant: " + email);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.COLLEGE_ADMIN);
        user.setAccountStatus(AccountStatus.ACTIVE);

        return CollegeAdminResponse.from(userRepository.save(user));
    }
}
