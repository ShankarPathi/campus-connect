package com.campusconnect.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A job offer released by a recruiter to a {@code SELECTED} student (architecture §5 {@code offers}; Story
 * 7.1, FR-23) — the recruiter-side of placement. <b>One offer per application</b>: the unique
 * {@code {tenantId, applicationId}} index is the concurrency backstop behind the service's pre-check, so a
 * second release for the same application is rejected (409 {@code CONFLICT}).
 *
 * <p>Created in {@link OfferStatus#PENDING} carrying the offer-letter PDF key, the denormalized
 * {@code studentId} (copied from the application — the offer is addressed to that student), and the four
 * terms (role, CTC in LPA, joining date, acceptance deadline). The {@code offerLetterKey} is <b>internal</b>
 * ({@code @JsonIgnore}) — the recruiter/student only ever receive a short-lived pre-signed URL, never the raw
 * storage key. No {@code @Version} here: 7.1 only ever inserts an offer (it never transitions one), so there
 * is no optimistic-lock concern until the offer state machine lands in 7.2/7.3.
 */
@Document("offers")
@CompoundIndex(name = "uniq_tenant_application", def = "{'tenantId': 1, 'applicationId': 1}", unique = true)
public class Offer extends TenantAwareDocument {

    private String applicationId;
    private String studentId;
    @JsonIgnore
    private String offerLetterKey;
    private String role;
    private Double ctc;
    private Instant joiningDate;
    private Instant acceptanceDeadline;
    private OfferStatus status = OfferStatus.PENDING;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getOfferLetterKey() {
        return offerLetterKey;
    }

    public void setOfferLetterKey(String offerLetterKey) {
        this.offerLetterKey = offerLetterKey;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Double getCtc() {
        return ctc;
    }

    public void setCtc(Double ctc) {
        this.ctc = ctc;
    }

    public Instant getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(Instant joiningDate) {
        this.joiningDate = joiningDate;
    }

    public Instant getAcceptanceDeadline() {
        return acceptanceDeadline;
    }

    public void setAcceptanceDeadline(Instant acceptanceDeadline) {
        this.acceptanceDeadline = acceptanceDeadline;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }
}
