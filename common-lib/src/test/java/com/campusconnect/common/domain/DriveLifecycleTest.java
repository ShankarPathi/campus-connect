package com.campusconnect.common.domain;

import com.campusconnect.common.exception.BusinessException;
import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The canonical drive state machine (Story 4.4, FR-11; architecture §8). */
class DriveLifecycleTest {

    @Test
    void legalEdges_areAllowed() {
        assertThat(DriveLifecycle.canTransition(DriveStatus.DRAFT, DriveStatus.PENDING_APPROVAL)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PENDING_APPROVAL, DriveStatus.PUBLISHED)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PENDING_APPROVAL, DriveStatus.REJECTED_BY_ADMIN)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PENDING_APPROVAL, DriveStatus.CANCELLED)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.REJECTED_BY_ADMIN, DriveStatus.PENDING_APPROVAL)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PUBLISHED, DriveStatus.ONGOING)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PUBLISHED, DriveStatus.CANCELLED)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.ONGOING, DriveStatus.CLOSED)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.ONGOING, DriveStatus.CANCELLED)).isTrue();
        assertThat(DriveLifecycle.canTransition(DriveStatus.CLOSED, DriveStatus.COMPLETED)).isTrue();
    }

    @Test
    void illegalEdges_areRejected() {
        assertThat(DriveLifecycle.canTransition(DriveStatus.DRAFT, DriveStatus.PUBLISHED)).isFalse();
        assertThat(DriveLifecycle.canTransition(DriveStatus.DRAFT, DriveStatus.CANCELLED)).isFalse();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PENDING_APPROVAL, DriveStatus.ONGOING)).isFalse();
        assertThat(DriveLifecycle.canTransition(DriveStatus.PUBLISHED, DriveStatus.DRAFT)).isFalse();
        assertThat(DriveLifecycle.canTransition(DriveStatus.REJECTED_BY_ADMIN, DriveStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStates_haveNoOutgoingEdges() {
        for (DriveStatus to : DriveStatus.values()) {
            assertThat(DriveLifecycle.canTransition(DriveStatus.CANCELLED, to)).isFalse();
            assertThat(DriveLifecycle.canTransition(DriveStatus.COMPLETED, to)).isFalse();
        }
    }

    @Test
    void requireTransition_throwsIllegalStateTransition_onIllegalMove() {
        assertThatThrownBy(() -> DriveLifecycle.requireTransition(DriveStatus.CANCELLED, DriveStatus.CANCELLED))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    void requireTransition_isSilent_onLegalMove() {
        assertThatCode(() -> DriveLifecycle.requireTransition(DriveStatus.PUBLISHED, DriveStatus.CANCELLED))
                .doesNotThrowAnyException();
    }
}
