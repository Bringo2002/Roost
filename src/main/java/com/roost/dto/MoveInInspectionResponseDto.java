package com.roost.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.model.MoveInInspection;

import java.time.format.DateTimeFormatter;

/**
 * Mirrors the Flutter MoveInInspection model's toJson/fromJson shape
 * field-for-field, so the existing client-side model needs essentially
 * no changes to consume this -- only the id type differs (a real
 * server-assigned Long here, serialized as a string since the Dart
 * model already types id as String?).
 */
public class MoveInInspectionResponseDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String id;
    private final Long propertyId;
    private final String propertyTitle;
    private final String tenantName;
    private final String landlordName;
    private final String createdAt;
    private final String completedAt;
    private final JsonNode rooms;
    private final String tenantSignature;
    private final String landlordSignature;
    private final String meterElectricity;
    private final String meterWater;

    public MoveInInspectionResponseDto(MoveInInspection inspection) {
        this.id = String.valueOf(inspection.getId());
        this.propertyId = inspection.getProperty().getId();
        this.propertyTitle = inspection.getProperty().getTitle();
        this.tenantName = inspection.getTenant().getName();
        this.landlordName = inspection.getLandlordName();
        this.createdAt = inspection.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.completedAt = inspection.getCompletedAt() != null
                ? inspection.getCompletedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;
        this.rooms = parseRoomsOrEmpty(inspection.getRoomsJson());
        this.tenantSignature = inspection.getTenantSignature();
        this.landlordSignature = inspection.getLandlordSignature();
        this.meterElectricity = inspection.getMeterElectricity();
        this.meterWater = inspection.getMeterWater();
    }

    /** Parses the stored rooms JSON blob back into a real JSON node so
     *  it serializes as a nested array, not a double-encoded string.
     *  Falls back to an empty array rather than throwing on malformed
     *  data -- a corrupted rooms blob shouldn't take down the whole
     *  response for what's otherwise a valid record. */
    private static JsonNode parseRoomsOrEmpty(String roomsJson) {
        try {
            return OBJECT_MAPPER.readTree(roomsJson);
        } catch (Exception e) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }

    public String getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public String getPropertyTitle() { return propertyTitle; }
    public String getTenantName() { return tenantName; }
    public String getLandlordName() { return landlordName; }
    public String getCreatedAt() { return createdAt; }
    public String getCompletedAt() { return completedAt; }
    public JsonNode getRooms() { return rooms; }
    public String getTenantSignature() { return tenantSignature; }
    public String getLandlordSignature() { return landlordSignature; }
    public String getMeterElectricity() { return meterElectricity; }
    public String getMeterWater() { return meterWater; }
}
