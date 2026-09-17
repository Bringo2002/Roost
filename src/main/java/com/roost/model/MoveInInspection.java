package com.roost.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A tenant's room-by-room move-in condition report, meant to serve as
 * durable evidence for deposit disputes months later.
 *
 * Previously this existed only in the Flutter client's in-memory widget
 * state, with the sole "save" action being a clipboard copy -- meaning
 * every inspection was lost the moment the app was backgrounded and
 * reclaimed, or the tenant switched away to paste the report somewhere.
 * A record whose whole purpose is being referenceable later cannot live
 * only in memory.
 *
 * [roomsJson] stores the room/item checklist as a JSON blob (matching
 * the Flutter model's own toJson/fromJson shape exactly) rather than as
 * separate Room/Item entities -- this data is always fetched whole by
 * inspection id, never queried or filtered at the SQL level, so a
 * fully-relational model would add real complexity (two more tables)
 * for no actual query benefit. Same TEXT-column-for-a-JSON-blob pattern
 * Message.java already uses.
 */
@Entity
@Table(name = "move_in_inspections")
public class MoveInInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    private String landlordName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String roomsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String tenantSignature;
    private String landlordSignature;
    private String meterElectricity;
    private String meterWater;

    public MoveInInspection() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public User getTenant() { return tenant; }
    public void setTenant(User tenant) { this.tenant = tenant; }

    public String getLandlordName() { return landlordName; }
    public void setLandlordName(String landlordName) { this.landlordName = landlordName; }

    public String getRoomsJson() { return roomsJson; }
    public void setRoomsJson(String roomsJson) { this.roomsJson = roomsJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getTenantSignature() { return tenantSignature; }
    public void setTenantSignature(String tenantSignature) { this.tenantSignature = tenantSignature; }

    public String getLandlordSignature() { return landlordSignature; }
    public void setLandlordSignature(String landlordSignature) { this.landlordSignature = landlordSignature; }

    public String getMeterElectricity() { return meterElectricity; }
    public void setMeterElectricity(String meterElectricity) { this.meterElectricity = meterElectricity; }

    public String getMeterWater() { return meterWater; }
    public void setMeterWater(String meterWater) { this.meterWater = meterWater; }
}
