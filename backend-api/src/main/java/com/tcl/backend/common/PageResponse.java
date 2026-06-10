package com.tcl.backend.common;

import java.util.List;
import org.springframework.data.domain.Page;

/*
 * Standard JSON response shape for paginated endpoints.
 *
 * Pagination means the backend returns one small part of a large result set.
 * Example: return 10 campaigns on page 1 instead of returning every campaign.
 *
 * <T> is a generic type. It lets this same wrapper work for any item type:
 * - PageResponse<CampaignResponse>
 * - PageResponse<TransactionResponse>
 */
public record PageResponse<T>(
        // Actual records for the current page.
        List<T> items,

        // Current page number exposed to the frontend. This project uses 1-based pages.
        int page,

        // Maximum number of items requested per page.
        int limit,

        // Total number of matching records across all pages.
        long totalItems,

        // Total number of pages available.
        int totalPages
) {

    /*
     * Converts Spring Data's Page<T> object into our API response shape.
     *
     * Spring Data uses 0-based page numbers internally:
     * - first page = 0
     *
     * Frontend/user APIs are usually easier with 1-based page numbers:
     * - first page = 1
     *
     * That is why we return page.getNumber() + 1.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
