package com.campusconnect.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ok_setsSuccessTrueWithDataAndNoError() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    void ok_withMessage_carriesMessage() {
        ApiResponse<String> response = ApiResponse.ok("payload", "done");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("done");
        assertThat(response.error()).isNull();
    }

    @Test
    void error_setsSuccessFalseWithErrorAndNoData() {
        ApiResponse<Void> response = ApiResponse.error(ApiError.of("NOT_FOUND", "missing"));

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("missing");
    }
}
