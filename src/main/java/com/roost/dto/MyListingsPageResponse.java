package com.roost.dto;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository.OwnerListingCounts;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * Response envelope for GET /api/v2/properties/my-listings.
 *
 * <pre>
 * {
 *   "items":   [ PropertyListItemDto, ... ],
 *   "page":    0,          // zero-based index of this page
 *   "size":    20,         // page size actually applied (may be capped)
 *   "hasNext": true,       // another page exists after this one
 *   "counts":  { "total": 42, "drafts": 3, "available": 30, "rented": 9, "verified": 25 }
 * }
 * </pre>
 *
 * {@code counts} always covers ALL of the owner's listings regardless of the
 * requested filter or page -- it feeds the dashboard's stats header and
 * filter-chip badges, which the client can no longer compute itself now that
 * it only holds the pages it has scrolled through.
 */
public record MyListingsPageResponse(
        List<PropertyListItemDto> items,
        int page,
        int size,
        boolean hasNext,
        Counts counts
) {

    public record Counts(long total, long drafts, long available, long rented, long verified) {

        /** SUM over zero rows is SQL NULL -- treat missing buckets as 0. */
        static Counts from(OwnerListingCounts c) {
            return new Counts(
                    nz(c == null ? null : c.getTotal()),
                    nz(c == null ? null : c.getDrafts()),
                    nz(c == null ? null : c.getAvailable()),
                    nz(c == null ? null : c.getRented()),
                    nz(c == null ? null : c.getVerified()));
        }

        private static long nz(Long v) {
            return v == null ? 0L : v;
        }
    }

    public static MyListingsPageResponse from(Slice<Property> slice, OwnerListingCounts counts) {
        return new MyListingsPageResponse(
                slice.getContent().stream().map(PropertyListItemDto::from).toList(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext(),
                Counts.from(counts));
    }
}
