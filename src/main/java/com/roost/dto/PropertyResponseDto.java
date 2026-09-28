package com.roost.dto;

import com.roost.model.Property;
import com.roost.model.User;
import com.roost.service.PropertyRiskService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Response shape for every read endpoint in PropertyController
 * (list, nearby, filter, get-by-id, my-listings, and the mutation
 * endpoints that hand the updated property back).
 *
 * Property itself must stay a plain JPA entity (see CLAUDE.md layering
 * rules), so returning it directly from a @RestController -- as every
 * one of these endpoints previously did -- serializes whatever Jackson
 * can reach through it, including the full owner User. That leaked the
 * owner's account email (and would leak more as User grows) to anyone
 * browsing listings, even though the frontend only reads the owner's
 * id/name/role/lastActiveAt. Routing every response through this DTO
 * makes the public shape explicit and independent of what fields
 * Property/User happen to carry internally.
 *
 * Two fields need a second, narrower redaction on top of that: Property
 * carries {@code endorsementToken} (a bearer credential -- anyone holding
 * it can call the unauthenticated {@code POST /api/properties/endorse/
 * {token}} and stamp "landlord endorsed" onto the listing, the trust badge
 * property cards display) and {@code ownerVerifyName}/{@code
 * ownerVerifyPhone} (a third party's personal contact details, entered by
 * a caretaker/agent on the owner's behalf). Serializing the entity
 * directly put these in every property card payload the app fetches --
 * anonymous visitors included. The three-argument constructor and the
 * {@code forOwner}/{@code forViewer} factories below control this: the
 * public view (used by every endpoint an anonymous visitor can reach)
 * redacts them to null; only a listing's owner, or whoever already holds
 * its endorsement token, sees the real values.
 */
public class PropertyResponseDto {

    private final Long id;
    private final String title;
    private final String buildingName;
    private final String description;
    private final String location;
    private final double price;
    private final int bedrooms;
    private final String type;
    private final String landlordPhone;
    private final String landlordName;
    private final String landlordId;
    private final boolean available;
    private final String imageUrl;
    private final boolean verified;
    private final boolean photoRejected;
    private final String photoRejectionReason;
    private final boolean gpsVerified;
    private final boolean communityVerified;
    private final String status;
    private final Double latitude;
    private final Double longitude;
    private final String videoUrl;
    private final String houseType;
    private final int bathrooms;
    private final boolean furnished;
    private final boolean parking;
    private final boolean water;
    private final boolean wifi;
    private final boolean security;
    private final boolean petFriendly;
    private final boolean balcony;
    private final boolean ac;
    private final boolean heating;
    private final boolean laundry;
    private final boolean dstv;
    private final boolean fence;
    private final boolean intercom;
    private final boolean elevator;
    private final boolean caretaker;
    private final boolean rooftop;
    private final boolean garden;
    private final boolean storage;
    private final boolean pool;
    private final boolean gym;
    private final boolean playArea;
    private final boolean cleaning;
    private final boolean garbage;
    private final boolean wheelchair;
    private final boolean solar;
    private final boolean generator;
    private final List<String> customAmenities;
    private final List<String> riskFlags;
    private final String deposit;
    private final String moveInDate;
    private final String country;
    private final LocalDateTime listedAt;
    private final LocalDateTime lastConfirmedAt;
    private final int viewCount;
    private final int saveCount;
    private final List<String> imageUrls;
    private final List<String> documentUrls;
    private final boolean documentVerified;
    private final boolean isDirectLandlord;
    private final boolean landlordEndorsed;
    private final String endorsementToken;
    private final String ownerVerifyName;
    private final String ownerVerifyPhone;
    private final String managerRole;
    private final String caretakerName;
    private final String caretakerPhone;
    private final boolean caretakerLivesOnSite;
    private final int depositMonths;
    private final double waterFee;
    private final double garbageFee;
    private final double serviceCharge;
    private final String electricityType;
    private final String nearbyFacilities;
    private final PropertyOwnerDto owner;
    private final Double averageRating;
    private final Long reviewCount;
    private final Long reportCount;

    // "Is this a fair price?" comparison -- only populated on the
    // single-property detail fetch (see PropertyController.getPropertyById),
    // never on list/filter endpoints, which stay as-is deliberately: computing
    // this per item would mean an extra query per card on every feed page.
    // Null means "not computed for this response" OR "no comparable listings
    // found" -- the frontend treats both the same way (don't show the card).
    private final Double priceComparisonAverage;
    private final Integer priceComparisonSampleSize;
    private final Double priceComparisonPercentDiff;

    /**
     * Builds the public view of a property: safe to serve to anyone,
     * including anonymous visitors browsing the feed. The endorsement
     * token and the verifier's contact details are redacted (see
     * {@link #PropertyResponseDto(Property, PropertyRiskService.PriceComparison, boolean)}).
     *
     * @param p the property to expose
     */
    public PropertyResponseDto(Property p) {
        this(p, null, false);
    }

    /**
     * Builds the public view of a property together with its price
     * comparison; same redaction as {@link #PropertyResponseDto(Property)}.
     *
     * @param p          the property to expose
     * @param comparison optional "is this a fair price" data, may be null
     */
    public PropertyResponseDto(Property p, PropertyRiskService.PriceComparison comparison) {
        this(p, comparison, false);
    }

    /**
     * Full constructor.
     *
     * <p>{@code includePrivateFields} controls three fields that must
     * never reach the public: {@code endorsementToken} (a bearer credential
     * -- whoever holds it can call the unauthenticated
     * {@code POST /api/properties/endorse/{token}} and stamp "landlord
     * endorsed", the trust badge property cards display) and the
     * verifier's {@code ownerVerifyName}/{@code ownerVerifyPhone} (a third
     * party's personal contact details). When false they serialize as
     * JSON {@code null}, so the response shape is unchanged for clients.
     *
     * @param p                     the property to expose
     * @param comparison            optional price comparison, may be null
     * @param includePrivateFields  true only for the listing's owner or the
     *                              holder of its endorsement token
     */
    public PropertyResponseDto(Property p, PropertyRiskService.PriceComparison comparison, boolean includePrivateFields) {
        this.id = p.getId();
        this.title = p.getTitle();
        this.buildingName = p.getBuildingName();
        this.description = p.getDescription();
        this.location = p.getLocation();
        this.price = p.getPrice();
        this.bedrooms = p.getBedrooms();
        this.type = p.getType();
        this.landlordPhone = p.getLandlordPhone();
        this.landlordName = p.getLandlordName();
        this.landlordId = p.getLandlordId();
        this.available = p.isAvailable();
        this.imageUrl = p.getImageUrl();
        this.verified = p.isVerified();
        this.photoRejected = p.isPhotoRejected();
        this.photoRejectionReason = p.getPhotoRejectionReason();
        this.gpsVerified = p.isGpsVerified();
        this.communityVerified = p.isCommunityVerified();
        this.status = p.getStatus();
        this.latitude = p.getLatitude();
        this.longitude = p.getLongitude();
        this.videoUrl = p.getVideoUrl();
        this.houseType = p.getHouseType();
        this.bathrooms = p.getBathrooms();
        this.furnished = p.isFurnished();
        this.parking = p.isParking();
        this.water = p.isWater();
        this.wifi = p.isWifi();
        this.security = p.isSecurity();
        this.petFriendly = p.isPetFriendly();
        this.balcony = p.isBalcony();
        this.ac = p.isAc();
        this.heating = p.isHeating();
        this.laundry = p.isLaundry();
        this.dstv = p.isDstv();
        this.fence = p.isFence();
        this.intercom = p.isIntercom();
        this.elevator = p.isElevator();
        this.caretaker = p.isCaretaker();
        this.rooftop = p.isRooftop();
        this.garden = p.isGarden();
        this.storage = p.isStorage();
        this.pool = p.isPool();
        this.gym = p.isGym();
        this.playArea = p.isPlayArea();
        this.cleaning = p.isCleaning();
        this.garbage = p.isGarbage();
        this.wheelchair = p.isWheelchair();
        this.solar = p.isSolar();
        this.generator = p.isGenerator();
        this.customAmenities = p.getCustomAmenities() != null ? p.getCustomAmenities() : new ArrayList<>();
        this.riskFlags = p.getRiskFlags() != null ? p.getRiskFlags() : new ArrayList<>();
        this.deposit = p.getDeposit();
        this.moveInDate = p.getMoveInDate();
        this.country = p.getCountry();
        this.listedAt = p.getListedAt();
        this.lastConfirmedAt = p.getLastConfirmedAt();
        this.viewCount = p.getViewCount();
        this.saveCount = p.getSaveCount();
        this.imageUrls = p.getImageUrls();
        this.documentUrls = p.getDocumentUrls() != null ? p.getDocumentUrls() : new ArrayList<>();
        this.documentVerified = Boolean.TRUE.equals(p.getDocumentVerified());
        this.isDirectLandlord = p.isDirectLandlord();
        this.landlordEndorsed = p.isLandlordEndorsed();
        this.endorsementToken = includePrivateFields ? p.getEndorsementToken() : null;
        this.ownerVerifyName = includePrivateFields ? p.getOwnerVerifyName() : null;
        this.ownerVerifyPhone = includePrivateFields ? p.getOwnerVerifyPhone() : null;
        this.managerRole = p.getManagerRole() != null ? p.getManagerRole() : "LANDLORD";
        this.caretakerName = p.getCaretakerName();
        this.caretakerPhone = p.getCaretakerPhone();
        this.caretakerLivesOnSite = p.isCaretakerLivesOnSite();
        this.depositMonths = p.getDepositMonths();
        this.waterFee = p.getWaterFee();
        this.garbageFee = p.getGarbageFee();
        this.serviceCharge = p.getServiceCharge();
        this.electricityType = p.getElectricityType() != null ? p.getElectricityType() : "TOKEN";
        this.nearbyFacilities = p.getNearbyFacilities();
        this.owner = PropertyOwnerDto.from(p.getOwner());
        this.averageRating = p.getAverageRating();
        this.reviewCount = p.getReviewCount();
        this.reportCount = p.getReportCount();

        if (comparison != null) {
            this.priceComparisonAverage = comparison.averagePrice();
            this.priceComparisonSampleSize = comparison.sampleSize();
            this.priceComparisonPercentDiff = comparison.percentDifference();
        } else {
            this.priceComparisonAverage = null;
            this.priceComparisonSampleSize = null;
            this.priceComparisonPercentDiff = null;
        }
    }

    /**
     * Public view (private fields redacted). This is the default for every
     * endpoint that anonymous users can reach.
     *
     * @param p the property, may be null
     * @return the public DTO, or null if {@code p} is null
     */
    public static PropertyResponseDto from(Property p) {
        return p == null ? null : new PropertyResponseDto(p);
    }

    /**
     * Public view with a price comparison attached.
     *
     * @param p          the property, may be null
     * @param comparison optional price comparison, may be null
     * @return the public DTO, or null if {@code p} is null
     */
    public static PropertyResponseDto from(Property p, PropertyRiskService.PriceComparison comparison) {
        return p == null ? null : new PropertyResponseDto(p, comparison);
    }

    /**
     * Public view of many properties.
     *
     * @param properties the properties to expose
     * @return one public DTO per property, in order
     */
    public static List<PropertyResponseDto> from(List<Property> properties) {
        return properties.stream().map(PropertyResponseDto::new).toList();
    }

    /**
     * Owner view (private fields included). Only for endpoints that have
     * already established the caller owns the listing or holds its
     * endorsement token.
     *
     * @param p the property, may be null
     * @return the owner DTO, or null if {@code p} is null
     */
    public static PropertyResponseDto forOwner(Property p) {
        return p == null ? null : new PropertyResponseDto(p, null, true);
    }

    /**
     * Owner view of many properties (e.g. the landlord's own dashboard).
     *
     * @param properties properties already known to belong to the caller
     * @return one owner DTO per property, in order
     */
    public static List<PropertyResponseDto> forOwner(List<Property> properties) {
        return properties.stream().map(PropertyResponseDto::forOwner).toList();
    }

    /**
     * Viewer-aware view: private fields are included only when
     * {@code viewer} is the listing's owner; everyone else (other users,
     * anonymous visitors) gets the public view.
     *
     * @param p          the property, may be null
     * @param comparison optional price comparison, may be null
     * @param viewer     the authenticated caller, or null if anonymous
     * @return the DTO appropriate for {@code viewer}, or null if {@code p} is null
     */
    public static PropertyResponseDto forViewer(Property p, PropertyRiskService.PriceComparison comparison, User viewer) {
        if (p == null) {
            return null;
        }
        boolean isOwner = viewer != null
                && viewer.getId() != null
                && p.getOwner() != null
                && viewer.getId().equals(p.getOwner().getId());
        return new PropertyResponseDto(p, comparison, isOwner);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBuildingName() { return buildingName; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public double getPrice() { return price; }
    public int getBedrooms() { return bedrooms; }
    public String getType() { return type; }
    public String getLandlordPhone() { return landlordPhone; }
    public String getLandlordName() { return landlordName; }
    public String getLandlordId() { return landlordId; }
    public boolean isAvailable() { return available; }
    public String getImageUrl() { return imageUrl; }
    public boolean isVerified() { return verified; }
    public boolean isPhotoRejected() { return photoRejected; }
    public String getPhotoRejectionReason() { return photoRejectionReason; }
    public boolean isGpsVerified() { return gpsVerified; }
    public boolean isCommunityVerified() { return communityVerified; }
    public String getStatus() { return status; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getVideoUrl() { return videoUrl; }
    public String getHouseType() { return houseType; }
    public int getBathrooms() { return bathrooms; }
    public boolean isFurnished() { return furnished; }
    public boolean isParking() { return parking; }
    public boolean isWater() { return water; }
    public boolean isWifi() { return wifi; }
    public boolean isSecurity() { return security; }
    public boolean isPetFriendly() { return petFriendly; }
    public boolean isBalcony() { return balcony; }
    public boolean isAc() { return ac; }
    public boolean isHeating() { return heating; }
    public boolean isLaundry() { return laundry; }
    public boolean isDstv() { return dstv; }
    public boolean isFence() { return fence; }
    public boolean isIntercom() { return intercom; }
    public boolean isElevator() { return elevator; }
    public boolean isCaretaker() { return caretaker; }
    public boolean isRooftop() { return rooftop; }
    public boolean isGarden() { return garden; }
    public boolean isStorage() { return storage; }
    public boolean isPool() { return pool; }
    public boolean isGym() { return gym; }
    public boolean isPlayArea() { return playArea; }
    public boolean isCleaning() { return cleaning; }
    public boolean isGarbage() { return garbage; }
    public boolean isWheelchair() { return wheelchair; }
    public boolean isSolar() { return solar; }
    public boolean isGenerator() { return generator; }
    public List<String> getCustomAmenities() { return customAmenities; }
    public List<String> getRiskFlags() { return riskFlags; }
    public String getDeposit() { return deposit; }
    public String getMoveInDate() { return moveInDate; }
    public String getCountry() { return country; }
    public LocalDateTime getListedAt() { return listedAt; }
    public LocalDateTime getLastConfirmedAt() { return lastConfirmedAt; }
    public int getViewCount() { return viewCount; }
    public int getSaveCount() { return saveCount; }
    public List<String> getImageUrls() { return imageUrls; }
    public List<String> getDocumentUrls() { return documentUrls; }
    public boolean isDocumentVerified() { return documentVerified; }
    public boolean isDirectLandlord() { return isDirectLandlord; }
    public boolean isLandlordEndorsed() { return landlordEndorsed; }
    public String getEndorsementToken() { return endorsementToken; }
    public String getOwnerVerifyName() { return ownerVerifyName; }
    public String getOwnerVerifyPhone() { return ownerVerifyPhone; }
    public String getManagerRole() { return managerRole; }
    public String getCaretakerName() { return caretakerName; }
    public String getCaretakerPhone() { return caretakerPhone; }
    public boolean isCaretakerLivesOnSite() { return caretakerLivesOnSite; }
    public int getDepositMonths() { return depositMonths; }
    public double getWaterFee() { return waterFee; }
    public double getGarbageFee() { return garbageFee; }
    public double getServiceCharge() { return serviceCharge; }
    public String getElectricityType() { return electricityType; }
    public String getNearbyFacilities() { return nearbyFacilities; }
    public PropertyOwnerDto getOwner() { return owner; }
    public Double getAverageRating() { return averageRating; }
    public Long getReviewCount() { return reviewCount; }
    public Long getReportCount() { return reportCount; }
    public Double getPriceComparisonAverage() { return priceComparisonAverage; }
    public Integer getPriceComparisonSampleSize() { return priceComparisonSampleSize; }
    public Double getPriceComparisonPercentDiff() { return priceComparisonPercentDiff; }
}
