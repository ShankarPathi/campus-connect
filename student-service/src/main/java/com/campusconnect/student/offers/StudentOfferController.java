package com.campusconnect.student.offers;

import com.campusconnect.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The student's offer endpoints (Story 7.3, FR-24). STUDENT-only; every action is owner-scoped in the service
 * (an offer that isn't the caller's own resolves to 404). View an offer (with a 15-min download URL), and
 * accept or decline it. This is the student-facing offer surface deferred from Story 7.1.
 */
@RestController
@RequestMapping("/api/student/offers")
@PreAuthorize("hasRole('STUDENT')")
public class StudentOfferController {

    private final StudentOfferService studentOfferService;

    public StudentOfferController(StudentOfferService studentOfferService) {
        this.studentOfferService = studentOfferService;
    }

    /** My offers (list). */
    @GetMapping
    public ApiResponse<List<OfferSummaryResponse>> list() {
        return ApiResponse.ok(studentOfferService.list());
    }

    /** One of my offers in detail + a fresh 15-minute offer-letter download URL. */
    @GetMapping("/{offerId}")
    public ApiResponse<OfferDetailResponse> view(@PathVariable String offerId) {
        return ApiResponse.ok(studentOfferService.view(offerId));
    }

    /** Accept the offer → placement record (pending confirmation) + profile flagged placed. */
    @PostMapping("/{offerId}/accept")
    public ApiResponse<OfferDetailResponse> accept(@PathVariable String offerId) {
        return ApiResponse.ok(studentOfferService.accept(offerId), "Offer accepted.");
    }

    /** Decline the offer. */
    @PostMapping("/{offerId}/decline")
    public ApiResponse<OfferDetailResponse> decline(@PathVariable String offerId) {
        return ApiResponse.ok(studentOfferService.decline(offerId), "Offer declined.");
    }
}
