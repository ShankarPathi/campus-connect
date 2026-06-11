package com.campusconnect.common.exception;

import com.campusconnect.common.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void carriesErrorCodeAndMessage() {
        BusinessException ex = new BusinessException(ErrorCode.NOT_FOUND, "missing");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ex.getErrorCode().status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("missing");
        assertThat(ex.getFields()).isNull();
    }

    @Test
    void subclassDefaultsToItsErrorCode() {
        DuplicateResourceException ex = new DuplicateResourceException("dup");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(ex.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void subclassCanCarrySpecificCode() {
        DuplicateResourceException ex =
                new DuplicateResourceException(ErrorCode.EMAIL_ALREADY_EXISTS, "taken");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        assertThat(ex.getErrorCode().status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("taken");
    }
}
