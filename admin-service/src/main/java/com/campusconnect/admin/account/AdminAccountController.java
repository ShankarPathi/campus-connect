package com.campusconnect.admin.account;

import com.campusconnect.common.auth.ChangePasswordRequest;
import com.campusconnect.common.auth.PasswordService;
import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated college-admin account settings (Story 2.4). Changing the password logs out all sessions. */
@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountController {

    private final PasswordService passwordService;

    public AdminAccountController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/password")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(TenantContext.getUserId(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(Boolean.TRUE, "Password changed — please log in again.");
    }
}
