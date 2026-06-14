package com.campusconnect.recruiter.drives;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recruiter drive-draft endpoints (Story 4.1, FR-10). Authenticated and restricted to RECRUITER; every
 * operation is owner-scoped in the service (a recruiter only ever sees/edits their own drives). An
 * approved recruiter is an ACTIVE one, so the 2.5 status gate already enforces "approved".
 */
@RestController
@RequestMapping("/api/recruiter/drives")
@PreAuthorize("hasRole('RECRUITER')")
public class DriveController {

    private final DriveService driveService;

    public DriveController(DriveService driveService) {
        this.driveService = driveService;
    }

    @PostMapping
    public ApiResponse<DriveResponse> create(@Valid @RequestBody DriveRequest request) {
        return ApiResponse.ok(driveService.create(request), "Drive draft created.");
    }

    @GetMapping
    public ApiResponse<List<DriveResponse>> list() {
        return ApiResponse.ok(driveService.listMyDrives());
    }

    @GetMapping("/{id}")
    public ApiResponse<DriveResponse> get(@PathVariable String id) {
        return ApiResponse.ok(driveService.getMyDrive(id));
    }

    /** PUT is a <b>full replace</b> of the editable fields (the 3.1 contract) — omitted fields are cleared, not merged. */
    @PutMapping("/{id}")
    public ApiResponse<DriveResponse> update(@PathVariable String id, @Valid @RequestBody DriveRequest request) {
        return ApiResponse.ok(driveService.update(id, request), "Drive draft updated.");
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<DriveResponse> submit(@PathVariable String id) {
        return ApiResponse.ok(driveService.submit(id), "Drive submitted for approval.");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DriveResponse> cancel(@PathVariable String id) {
        return ApiResponse.ok(driveService.cancel(id), "Drive cancelled.");
    }
}
