package com.campusconnect.common.web;

import java.util.List;

/**
 * The standard paginated payload carried as the {@code data} of a list endpoint's
 * {@link ApiResponse}. Default page size is {@link #DEFAULT_PAGE_SIZE}.
 */
public record PageResponse<T>(List<T> items, long totalCount, int page, int pageSize, int totalPages) {

    /** Default page size for every list endpoint (architecture §12). */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Builds a page, computing {@code totalPages} from the total count and page size.
     * A non-positive page size yields {@code 0} pages (defensive — callers should pass &gt; 0).
     */
    public static <T> PageResponse<T> of(List<T> items, long totalCount, int page, int pageSize) {
        List<T> safeItems = items != null ? items : List.of();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new PageResponse<>(safeItems, totalCount, page, pageSize, totalPages);
    }
}
