package com.campusconnect.student.account;

import com.campusconnect.common.auth.ChangePasswordRequest;
import com.campusconnect.common.auth.PasswordService;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated student account settings (Story 2.4). This path is not a public auth path, so the
 * security chain requires a JWT; the current user comes from {@link TenantContext}. Changing the
 * password logs the user out of all sessions.
 */
@RestController
@RequestMapping("/api/student/account")
public class StudentAccountController {

    private final PasswordService passwordService;

    public StudentAccountController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/password")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(TenantContext.getUserId(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(Boolean.TRUE, "Password changed — please log in again.");
    }
}
