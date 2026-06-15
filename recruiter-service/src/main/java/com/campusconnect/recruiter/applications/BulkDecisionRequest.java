package com.campusconnect.recruiter.applications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body for a shortlist/reject action (Story 6.2, FR-19). The same shape serves single and bulk — a
 * one-element list is the "single" case. {@code applicationIds} must be non-empty, capped, and each id
 * non-blank (a malformed request → 400 {@code VALIDATION_ERROR}, so a blank id is rejected up front rather
 * than surfacing as a misleading per-item "not found"); duplicates are de-duplicated in the service.
 */
public record BulkDecisionRequest(
        @NotEmpty @Size(max = 200) List<@NotBlank String> applicationIds) {
}
