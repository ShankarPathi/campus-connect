package com.campusconnect.common.web;

import com.campusconnect.common.exception.BadRequestException;
import com.campusconnect.common.exception.DuplicateResourceException;
import com.campusconnect.common.exception.ForbiddenException;
import com.campusconnect.common.exception.RateLimitException;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link GlobalExceptionHandler} and the envelope end-to-end through a standalone
 * MockMvc setup — controller + advice only, no Spring Boot context.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void success_returnsEnvelopeWithDataAndNoError() throws Exception {
        mockMvc.perform(get("/t/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").value("v"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void page_returnsPageResponseShape() throws Exception {
        mockMvc.perform(get("/t/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void notFound_maps404() throws Exception {
        mockMvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void duplicate_maps409GenericCode() throws Exception {
        mockMvc.perform(get("/t/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void specificCode_flowsThrough() throws Exception {
        mockMvc.perform(get("/t/email-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void unauthorized_maps401() throws Exception {
        mockMvc.perform(get("/t/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void forbidden_maps403() throws Exception {
        mockMvc.perform(get("/t/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void badRequest_maps400() throws Exception {
        mockMvc.perform(get("/t/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void rateLimited_maps429() throws Exception {
        mockMvc.perform(get("/t/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void validationError_maps400WithFields() throws Exception {
        mockMvc.perform(post("/t/validate")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists());
    }

    @Test
    void unexpectedException_maps500AndNeverLeaksStackTrace() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"))
                // the real cause and any stack trace must not appear in the body
                .andExpect(content().string(not(containsString("internal detail"))))
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedJsonBody_maps400NotFiveHundred() throws Exception {
        mockMvc.perform(post("/t/validate")
                        .contentType("application/json")
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                // the parse error detail must not leak
                .andExpect(content().string(not(containsString("JsonParseException"))));
    }

    @Test
    void wrongHttpMethod_maps405() throws Exception {
        mockMvc.perform(put("/t/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void accessDenied_maps403NotFiveHundred() throws Exception {
        mockMvc.perform(get("/t/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void duplicateKey_maps409AndDoesNotLeakDbError() throws Exception {
        mockMvc.perform(get("/t/dup-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("E11000"))));
    }

    @Test
    void constraintViolation_maps400WithLeafFieldName() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Account>> violations = validator.validate(new Account(""));
        var ex = new jakarta.validation.ConstraintViolationException(violations);

        ResponseEntity<Object> response = new GlobalExceptionHandler().handleConstraintViolation(ex);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body).isNotNull();
        assertThat(body.error().code()).isEqualTo("VALIDATION_ERROR");
        // keyed by the leaf property name, not an internal method/arg path
        assertThat(body.error().fields()).containsKey("email");
    }

    record Account(@NotBlank String email) {
    }

    // ── test fixture ──

    record Data(String value) {
    }

    record NameBody(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/t")
    static class FixtureController {

        @GetMapping("/success")
        ApiResponse<Data> success() {
            return ApiResponse.ok(new Data("v"));
        }

        @GetMapping("/page")
        ApiResponse<PageResponse<String>> page() {
            return ApiResponse.ok(PageResponse.of(List.of("a", "b"), 2, 1, 20));
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("missing");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DuplicateResourceException("dup");
        }

        @GetMapping("/email-exists")
        void emailExists() {
            throw new DuplicateResourceException(ErrorCode.EMAIL_ALREADY_EXISTS, "taken");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("no");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("nope");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("bad");
        }

        @GetMapping("/rate-limited")
        void rateLimited() {
            throw new RateLimitException("slow down");
        }

        @PostMapping("/validate")
        ApiResponse<Void> validate(@Valid @RequestBody NameBody body) {
            return ApiResponse.ok(null);
        }

        @GetMapping("/boom")
        void boom() {
            throw new RuntimeException("internal detail must not leak");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            // simulates a @PreAuthorize denial reaching the advice (not the filter)
            throw new org.springframework.security.access.AccessDeniedException("denied");
        }

        @GetMapping("/dup-key")
        void dupKey() {
            // simulates a unique-index violation (e.g. a TOCTOU race) surfacing from the data layer
            throw new org.springframework.dao.DuplicateKeyException("E11000 duplicate key error: slug");
        }
    }
}
