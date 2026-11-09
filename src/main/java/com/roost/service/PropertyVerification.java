package com.roost.service;

import com.roost.model.Property;

/**
 * The single definition of what Roost has and hasn't checked about a listing.
 *
 * <p>Each trust signal has its own explicit state, so "uploaded" is never
 * confused with "approved", "entered" with "verified", or "was at the
 * property" with "owns it". The Verified badge is derived from these states
 * here and nowhere else; clients display what this says instead of assembling
 * a badge from their own flags.
 *
 * <p>The state sets are the full vocabulary. Some states can't be produced
 * yet because nothing records them: attempt history (PENDING / FAILED on-site
 * checks), reviewer activity (UNDER_REVIEW, document REJECTED) and authority
 * checks. Those are reserved for when that data exists, so clients can be
 * written against the complete set now.
 */
public record PropertyVerification(
        PhoneState phone,
        LocationState location,
        OnSiteState onSite,
        PhotosState photos,
        DocumentsState documents,
        AuthorityState authority,
        boolean badgeEarned) {

    /** The listing owner's phone number. */
    public enum PhoneState {
        UNVERIFIED,
        /** Reserved: a code has been sent but not yet confirmed. */
        PENDING,
        VERIFIED
    }

    /** Whether the listing has a pinned location. */
    public enum LocationState {
        UNCONFIRMED,
        CONFIRMED,
        /** Reserved: the pin moved after an on-site check. */
        CHANGED
    }

    /** Whether the lister has been checked as physically present at the pin. */
    public enum OnSiteState {
        NOT_ATTEMPTED,
        /** Reserved. */
        PENDING,
        PASSED,
        /** Reserved: needs a record of attempts. */
        FAILED
    }

    /** The listing's photos. */
    public enum PhotosState {
        NOT_SUBMITTED,
        /** Uploaded and waiting for a reviewer's decision. */
        SUBMITTED,
        /** Reserved: a reviewer has started on them. */
        UNDER_REVIEW,
        APPROVED,
        REJECTED
    }

    /** Supporting documents. Uploading one never implies it was accepted. */
    public enum DocumentsState {
        NOT_SUBMITTED,
        /** Uploaded and waiting for a reviewer's decision. */
        UPLOADED,
        /** Reserved. */
        UNDER_REVIEW,
        ACCEPTED,
        /** Reserved: needs a record of the decision. */
        REJECTED
    }

    /** Whether the lister is the owner or authorised to list for them. Not checked yet. */
    public enum AuthorityState {
        NOT_ESTABLISHED,
        /** Reserved. */
        PENDING,
        /** Reserved. */
        CONFIRMED,
        /** Reserved. */
        REJECTED
    }

    /** Derives every state, and the badge, from what is stored about the listing. */
    public static PropertyVerification from(Property property) {
        PhoneState phone = property.getOwner() != null && property.getOwner().isPhoneVerified()
                ? PhoneState.VERIFIED
                : PhoneState.UNVERIFIED;
        LocationState location = property.getLatitude() != null && property.getLongitude() != null
                ? LocationState.CONFIRMED
                : LocationState.UNCONFIRMED;
        OnSiteState onSite = property.isGpsVerified() ? OnSiteState.PASSED : OnSiteState.NOT_ATTEMPTED;
        PhotosState photos = photosState(property);
        DocumentsState documents = documentsState(property);

        boolean badgeEarned = phone == PhoneState.VERIFIED
                && onSite == OnSiteState.PASSED
                && photos == PhotosState.APPROVED;

        return new PropertyVerification(
                phone, location, onSite, photos, documents, AuthorityState.NOT_ESTABLISHED, badgeEarned);
    }

    private static PhotosState photosState(Property property) {
        if (property.isPhotoApproved()) {
            return PhotosState.APPROVED;
        }
        if (property.isPhotoRejected()) {
            return PhotosState.REJECTED;
        }
        boolean hasPhotos = property.getImageUrls() != null
                && property.getImageUrls().stream().anyMatch(url -> url != null && !url.isBlank());
        return hasPhotos ? PhotosState.SUBMITTED : PhotosState.NOT_SUBMITTED;
    }

    private static DocumentsState documentsState(Property property) {
        if (Boolean.TRUE.equals(property.getDocumentVerified())) {
            return DocumentsState.ACCEPTED;
        }
        boolean hasDocuments = property.getDocumentUrls() != null
                && property.getDocumentUrls().stream().anyMatch(url -> url != null && !url.isBlank());
        return hasDocuments ? DocumentsState.UPLOADED : DocumentsState.NOT_SUBMITTED;
    }
}
