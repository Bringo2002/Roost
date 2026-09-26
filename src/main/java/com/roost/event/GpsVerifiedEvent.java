package com.roost.event;

/**
 * Raised after a property's GPS verification transaction has committed.
 * Listened for by NearbyFacilitiesUpdateListener to populate the
 * nearbyFacilities field out-of-band, since that lookup hits a free
 * third-party API (Overpass) with no SLA and shouldn't be on the
 * critical path of the verification request itself.
 */
public record GpsVerifiedEvent(Long propertyId, double latitude, double longitude) {
}
