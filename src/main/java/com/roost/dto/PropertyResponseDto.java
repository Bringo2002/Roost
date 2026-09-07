package com.roost.dto;

import com.roost.model.Property;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Public response shape for every read endpoint in PropertyController
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
    private final String deposit;
    private final String moveInDate;
    private final String country;
    private final LocalDateTime listedAt;
    private final LocalDateTime lastConfirmedAt;
    private final int viewCount;
    private final int saveCount;
    private final List<String> imageUrls;
    private final PropertyOwnerDto owner;
    private final Double averageRating;
    private final Long reviewCount;
    private final Long reportCount;

    public PropertyResponseDto(Property p) {
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
        this.deposit = p.getDeposit();
        this.moveInDate = p.getMoveInDate();
        this.country = p.getCountry();
        this.listedAt = p.getListedAt();
        this.lastConfirmedAt = p.getLastConfirmedAt();
        this.viewCount = p.getViewCount();
        this.saveCount = p.getSaveCount();
        this.imageUrls = p.getImageUrls();
        this.owner = PropertyOwnerDto.from(p.getOwner());
        this.averageRating = p.getAverageRating();
        this.reviewCount = p.getReviewCount();
        this.reportCount = p.getReportCount();
    }

    public static PropertyResponseDto from(Property p) {
        return p == null ? null : new PropertyResponseDto(p);
    }

    public static List<PropertyResponseDto> from(List<Property> properties) {
        return properties.stream().map(PropertyResponseDto::new).toList();
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
    public String getDeposit() { return deposit; }
    public String getMoveInDate() { return moveInDate; }
    public String getCountry() { return country; }
    public LocalDateTime getListedAt() { return listedAt; }
    public LocalDateTime getLastConfirmedAt() { return lastConfirmedAt; }
    public int getViewCount() { return viewCount; }
    public int getSaveCount() { return saveCount; }
    public List<String> getImageUrls() { return imageUrls; }
    public PropertyOwnerDto getOwner() { return owner; }
    public Double getAverageRating() { return averageRating; }
    public Long getReviewCount() { return reviewCount; }
    public Long getReportCount() { return reportCount; }
}
