package com.roost.dto;

import com.roost.model.Property;

import java.time.LocalDateTime;

/**
 * Slim, list-view shape of a Property, used by GET /api/v2/properties/my-listings.
 *
 * Deliberately contains only plain scalar columns -- no amenity booleans, no
 * description, no coordinates, and above all none of Property's four
 * @ElementCollection lists (images, documents, risk flags, custom amenities),
 * so mapping a page of these never triggers collection loading. Clients that
 * need the full record (e.g. to pre-fill the edit form) fetch it by id from
 * GET /api/properties/{id}, which returns {@link PropertyResponseDto}.
 *
 * Field names and types are identical to the same-named fields in
 * PropertyResponseDto, so a client's existing JSON parsing keeps working.
 * landlordPhone is intentionally omitted: nothing on the dashboard list
 * needs it.
 */
public record PropertyListItemDto(
        Long id,
        String title,
        String location,
        double price,
        int bedrooms,
        String type,
        boolean available,
        String imageUrl,
        boolean verified,
        boolean gpsVerified,
        String status,
        LocalDateTime listedAt,
        boolean landlordEndorsed,
        String endorsementToken,
        String ownerVerifyName,
        String ownerVerifyPhone,
        String managerRole
) {

    public static PropertyListItemDto from(Property p) {
        return new PropertyListItemDto(
                p.getId(),
                p.getTitle(),
                p.getLocation(),
                p.getPrice(),
                p.getBedrooms(),
                p.getType(),
                p.isAvailable(),
                p.getImageUrl(),
                p.isVerified(),
                p.isGpsVerified(),
                p.getStatus(),
                p.getListedAt(),
                p.isLandlordEndorsed(),
                p.getEndorsementToken(),
                p.getOwnerVerifyName(),
                p.getOwnerVerifyPhone(),
                p.getManagerRole() != null ? p.getManagerRole() : "LANDLORD"
        );
    }
}
