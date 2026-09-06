package com.roost.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    /** Optional -- the apartment/building/compound's own name (e.g.
     *  "Sunrise Apartments"), distinct from the listing title. Many
     *  properties genuinely don't have one (standalone houses, unnamed
     *  compounds), so this is never required. */
    private String buildingName;

    private String location;
    private double price;
    private int bedrooms;
    private String type; // rental, sale, airbnb
    private Boolean available = true;
    private String landlordPhone;
    private String landlordName;
    private String landlordId;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    /**
     * The "Verified" badge shown throughout the app. Computed, not set
     * directly by clients -- see PropertyService.recomputeVerification.
     * True only when all three v1 verification signals are met: owner's
     * phone verified, GPS location genuinely confirmed on-site
     * (gpsVerified, not just lat/lng being present), and photos
     * admin-approved.
     */
    private Boolean verified = false;

    /** Admin sign-off that photos are real (not stock/screenshots).
     *  One of the three signals composing [verified]. */
    private Boolean photoApproved = false;

    /**
     * Set whenever an admin takes a moderation action on this listing's
     * reports (dismiss, hide, or restore). Reports created after this
     * timestamp -- or all reports, if this is null -- are what the
     * flagged-listings queue surfaces; this lets a single fresh report
     * reappear in the queue even after an earlier batch was reviewed,
     * without ever losing report history.
     */
    private java.time.LocalDateTime reportsReviewedAt;

    /**
     * True only after the owner physically stood at the property and
     * had their live device GPS position checked against the listing's
     * pinned coordinates (see PropertyService.verifyGpsLocation) --
     * replaces the old placeholder check that just tested whether
     * latitude/longitude were non-null, which anyone could satisfy by
     * dropping a map pin from anywhere.
     */
    private Boolean gpsVerified = false;

    private LocalDateTime gpsVerifiedAt;

    /**
     * True once enough distinct tenants who genuinely engaged with this
     * listing (applied to it) have confirmed it matched what was
     * advertised. A separate trust signal from [verified] -- this one
     * reflects renter experience, not landlord-provided proof.
     */
    private Boolean communityVerified = false;

    /** DRAFT or PUBLISHED. Drafts are never returned by public feed/
     *  search/nearby/filter queries and are only visible to their owner
     *  via GET /{id} -- see PropertyRepository and PropertyController.
     *  Defaults to PUBLISHED so every pre-existing row (created before
     *  this field existed) behaves exactly as it did before. */
    private String status = "PUBLISHED";

    private Double latitude;

    private Double longitude;

    private String videoUrl;

    private String houseType; // BEDSITTER, STUDIO, 1BR, 2BR, 3BR
    private int bathrooms;
    private Boolean furnished = false;
    private Boolean parking = false;
    private Boolean water = false;
    private Boolean wifi = false;
    private Boolean security = false;
    private Boolean petFriendly = false;
    private Boolean balcony = false;
    private String deposit;
    private String moveInDate;
    private String country = "KE";

    private LocalDateTime listedAt = LocalDateTime.now();
    private LocalDateTime lastConfirmedAt = LocalDateTime.now();

    /** Set when the 7-day "is this still available?" reminder push is
     *  sent; cleared whenever the landlord confirms or otherwise touches
     *  availability. Null means no reminder is currently pending. */
    private LocalDateTime remindedAt;

    private int viewCount = 0;
    private int saveCount = 0;

    @ElementCollection
    @CollectionTable(name = "property_image_urls", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @Transient
    private Double averageRating;

    @Transient
    private Long reviewCount;

    /** Not persisted -- populated only when returning a property to the
     *  admin flagged-listings queue (see PropertyService.getFlaggedForReview).
     *  Total report count, regardless of review state. */
    @Transient
    private Long reportCount;

    public Property() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(int bedrooms) {
        this.bedrooms = bedrooms;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isAvailable() {
        return available != null && available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public String getLandlordPhone() {
        return landlordPhone;
    }

    public void setLandlordPhone(String landlordPhone) {
        this.landlordPhone = landlordPhone;
    }

    public String getLandlordName() {
        return landlordName;
    }

    public void setLandlordName(String landlordName) {
        this.landlordName = landlordName;
    }

    public String getLandlordId() {
        return landlordId;
    }

    public void setLandlordId(String landlordId) {
        this.landlordId = landlordId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isVerified() {
        return verified != null && verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public boolean isPhotoApproved() {
        return photoApproved != null && photoApproved;
    }

    public void setPhotoApproved(Boolean photoApproved) {
        this.photoApproved = photoApproved;
    }

    public java.time.LocalDateTime getReportsReviewedAt() {
        return reportsReviewedAt;
    }

    public void setReportsReviewedAt(java.time.LocalDateTime reportsReviewedAt) {
        this.reportsReviewedAt = reportsReviewedAt;
    }

    public boolean isGpsVerified() {
        return gpsVerified != null && gpsVerified;
    }

    public void setGpsVerified(Boolean gpsVerified) {
        this.gpsVerified = gpsVerified;
    }

    public LocalDateTime getGpsVerifiedAt() {
        return gpsVerifiedAt;
    }

    public void setGpsVerifiedAt(LocalDateTime gpsVerifiedAt) {
        this.gpsVerifiedAt = gpsVerifiedAt;
    }

    public boolean isCommunityVerified() {
        return communityVerified != null && communityVerified;
    }

    public void setCommunityVerified(Boolean communityVerified) {
        this.communityVerified = communityVerified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getHouseType() {
        return houseType;
    }

    public void setHouseType(String houseType) {
        this.houseType = houseType;
    }

    public int getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(int bathrooms) {
        this.bathrooms = bathrooms;
    }

    public boolean isFurnished() {
        return furnished != null && furnished;
    }

    public void setFurnished(Boolean furnished) {
        this.furnished = furnished;
    }

    public boolean isParking() {
        return parking != null && parking;
    }

    public void setParking(Boolean parking) {
        this.parking = parking;
    }

    public boolean isWater() {
        return water != null && water;
    }

    public void setWater(Boolean water) {
        this.water = water;
    }

    public boolean isWifi() {
        return wifi != null && wifi;
    }

    public void setWifi(Boolean wifi) {
        this.wifi = wifi;
    }

    public boolean isSecurity() {
        return security != null && security;
    }

    public void setSecurity(Boolean security) {
        this.security = security;
    }

    public boolean isPetFriendly() {
        return petFriendly != null && petFriendly;
    }

    public void setPetFriendly(Boolean petFriendly) {
        this.petFriendly = petFriendly;
    }

    public boolean isBalcony() {
        return balcony != null && balcony;
    }

    public void setBalcony(Boolean balcony) {
        this.balcony = balcony;
    }

    public String getDeposit() {
        return deposit;
    }

    public void setDeposit(String deposit) {
        this.deposit = deposit;
    }

    public String getMoveInDate() {
        return moveInDate;
    }

    public void setMoveInDate(String moveInDate) {
        this.moveInDate = moveInDate;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public LocalDateTime getListedAt() {
        return listedAt;
    }

    public void setListedAt(LocalDateTime listedAt) {
        this.listedAt = listedAt;
    }

    public LocalDateTime getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public void setLastConfirmedAt(LocalDateTime lastConfirmedAt) {
        this.lastConfirmedAt = lastConfirmedAt;
    }

    public LocalDateTime getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(LocalDateTime remindedAt) {
        this.remindedAt = remindedAt;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public void setSaveCount(int saveCount) {
        this.saveCount = saveCount;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public String getLandlordPhoneOrPlaceholder() {
        return landlordPhone != null ? landlordPhone : "";
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Long getReportCount() {
        return reportCount;
    }

    public void setReportCount(Long reportCount) {
        this.reportCount = reportCount;
    }

    /**
     * Cached JSON array of nearby points of interest -- closest mall,
     * hospital, and major road -- with their distance from this listing,
     * e.g. [{"name":"TRM Mall","category":"mall","distanceMeters":600}].
     * Computed once via NearbyFacilitiesService whenever GPS verification
     * succeeds (see PropertyService.verifyGpsLocation), not re-queried
     * live per pageview -- coordinates don't move, so there's no reason
     * to re-fetch from Overpass every time a renter opens the listing.
     * Stored as raw JSON text rather than a structured column; the
     * frontend decodes it directly rather than this being a nested
     * object in the API response.
     */
    @Column(columnDefinition = "TEXT")
    private String nearbyFacilities;

    public String getNearbyFacilities() {
        return nearbyFacilities;
    }

    public void setNearbyFacilities(String nearbyFacilities) {
        this.nearbyFacilities = nearbyFacilities;
    }
}