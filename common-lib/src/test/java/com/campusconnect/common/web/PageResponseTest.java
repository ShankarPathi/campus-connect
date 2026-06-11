package com.campusconnect.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void defaultPageSizeIsTwenty() {
        assertThat(PageResponse.DEFAULT_PAGE_SIZE).isEqualTo(20);
    }

    @Test
    void emptyPage_hasZeroTotalPages() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 1, 20);
        assertThat(page.totalPages()).isZero();
        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void exactMultiple_dividesEvenly() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 40, 1, 20);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void remainder_roundsUp() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 41, 1, 20);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void singlePartialPage_isOnePage() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 5, 1, 20);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
    }

    @Test
    void nullItems_defaultsToEmptyList() {
        PageResponse<String> page = PageResponse.of(null, 0, 1, 20);
        assertThat(page.items()).isNotNull().isEmpty();
    }
}
