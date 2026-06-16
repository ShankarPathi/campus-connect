package com.campusconnect.recruiter.offers;

import com.campusconnect.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recruiter offer-release endpoint (Story 7.1, FR-23). RECRUITER-only; nested under the application within an
 * owned drive, so the service's owner-scoped drive load + drive-scoped application load are the access gate
 * (a non-owned / cross-tenant / wrong-drive target resolves to 404). Multipart: a {@code file} part (the
 * offer-letter PDF) and a typed JSON {@code data} part (the terms). 7.1 is the recruiter-side release only —
 * the student offer view + accept/decline is Story 7.3, and the student notification is Epic 8.
 */
@RestController
@RequestMapping("/api/recruiter/drives/{driveId}/applicants/{applicationId}/offer")
@PreAuthorize("hasRole('RECRUITER')")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /** Release an offer (PDF + terms) for a SELECTED applicant; returns the offer with a 15-min verify URL. */
    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<OfferResponse> release(@PathVariable String driveId,
                                              @PathVariable String applicationId,
                                              @RequestPart("file") MultipartFile file,
                                              @RequestPart("data") @Valid ReleaseOfferRequest data) {
        return ApiResponse.ok(offerService.release(driveId, applicationId, data, file), "Offer released.");
    }
}
