package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The canonical Application state machine (Story 5.5, FR-16; architecture §8). Pure unit tests. */
class ApplicationLifecycleTest {

    // ── requireWithdrawable: legal only pre-shortlist ───────────────────────────────────────────────

    @Test
    void withdrawable_fromApplied() {
        ApplicationLifecycle.requireWithdrawable(ApplicationStatus.APPLIED); // no throw
    }

    @Test
    void withdrawable_fromUnderReview() {
        ApplicationLifecycle.requireWithdrawable(ApplicationStatus.UNDER_REVIEW); // no throw
    }

    @Test
    void notWithdrawable_fromShortlistedOrLater_throwsWithdrawNotAllowed() {
        for (ApplicationStatus blocked : new ApplicationStatus[]{
                ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEWING, ApplicationStatus.SELECTED,
                ApplicationStatus.OFFER_RELEASED, ApplicationStatus.OFFER_ACCEPTED, ApplicationStatus.OFFER_DECLINED,
                ApplicationStatus.OFFER_EXPIRED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN}) {
            assertThatExceptionOfType(BusinessException.class)
                    .as("withdraw must be blocked from %s", blocked)
                    .isThrownBy(() -> ApplicationLifecycle.requireWithdrawable(blocked))
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_NOT_ALLOWED));
        }
    }

    // ── canTransition / requireTransition ───────────────────────────────────────────────────────────

    @Test
    void canTransition_legalEdges() {
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW)).isTrue();
        // Story 6.2: shortlist straight from APPLIED (no story produces UNDER_REVIEW; 6.1 list is read-only)
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED)).isTrue();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED)).isTrue();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEWING)).isTrue();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.INTERVIEWING, ApplicationStatus.INTERVIEWING)).isTrue();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.SELECTED, ApplicationStatus.OFFER_RELEASED)).isTrue();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.OFFER_RELEASED, ApplicationStatus.OFFER_ACCEPTED)).isTrue();
    }

    @Test
    void canTransition_illegalEdges() {
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.SELECTED)).isFalse();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.SHORTLISTED, ApplicationStatus.WITHDRAWN)).isFalse();
        assertThat(ApplicationLifecycle.canTransition(ApplicationStatus.WITHDRAWN, ApplicationStatus.APPLIED)).isFalse();
    }

    @Test
    void requireTransition_illegal_throwsIllegalStateTransition() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> ApplicationLifecycle.requireTransition(
                        ApplicationStatus.APPLIED, ApplicationStatus.SELECTED))
                .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    void requireTransition_legal_doesNotThrow() {
        ApplicationLifecycle.requireTransition(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN); // no throw
    }

    // ── terminal states have no outgoing edges ──────────────────────────────────────────────────────

    @Test
    void terminalStates_haveNoOutgoingEdges() {
        for (ApplicationStatus terminal : new ApplicationStatus[]{
                ApplicationStatus.OFFER_ACCEPTED, ApplicationStatus.OFFER_DECLINED, ApplicationStatus.OFFER_EXPIRED,
                ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN}) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                assertThat(ApplicationLifecycle.canTransition(terminal, to))
                        .as("%s should be terminal (no edge to %s)", terminal, to).isFalse();
            }
        }
    }

    @Test
    void noSelfEdge_exceptInterviewing() {
        for (ApplicationStatus s : ApplicationStatus.values()) {
            boolean expectedSelfLoop = s == ApplicationStatus.INTERVIEWING; // multi-round is the only self-edge
            assertThat(ApplicationLifecycle.canTransition(s, s))
                    .as("self-edge for %s", s).isEqualTo(expectedSelfLoop);
        }
    }

    @Test
    void withdrawable_messageNamesStatus() {
        assertThatThrownBy(() -> ApplicationLifecycle.requireWithdrawable(ApplicationStatus.SHORTLISTED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SHORTLISTED");
    }
}
