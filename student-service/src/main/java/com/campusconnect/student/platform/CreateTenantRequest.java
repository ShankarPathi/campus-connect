package com.campusconnect.student.platform;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request to provision a new college tenant (FR-1). {@code subdomain} is optional and defaults to
 * {@code slug}. Slug and subdomain must be DNS-friendly (lowercase, alphanumeric, single hyphens).
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 200) String name,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "slug must be lowercase alphanumeric with single hyphens")
        String slug,

        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "subdomain must be lowercase alphanumeric with single hyphens")
        String subdomain,

        @NotEmpty @Size(max = 50) List<@NotBlank String> branches,
        @NotEmpty @Size(max = 50) List<@NotBlank String> batches,
        @NotNull LocalDate seasonStart,
        @NotNull LocalDate seasonEnd) {

    /** Cross-field: the season must not end before it starts (null dates are left to {@code @NotNull}). */
    @AssertTrue(message = "seasonEnd must not be before seasonStart")
    public boolean isSeasonRangeValid() {
        return seasonStart == null || seasonEnd == null || !seasonEnd.isBefore(seasonStart);
    }
}
