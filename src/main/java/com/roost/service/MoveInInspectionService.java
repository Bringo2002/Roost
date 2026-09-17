package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.MoveInInspectionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Server-side persistence for move-in inspections -- previously this
 * data existed only in the Flutter client's in-memory widget state (see
 * the frontend's MoveInInspection model), with a clipboard copy as the
 * only "save" action. Every inspection was lost the moment the app was
 * backgrounded and reclaimed. This gives it a real, durable home.
 */
@Service
public class MoveInInspectionService {

    private final MoveInInspectionRepository moveInInspectionRepository;
    private final PropertyService propertyService;

    public MoveInInspectionService(MoveInInspectionRepository moveInInspectionRepository,
                                    PropertyService propertyService) {
        this.moveInInspectionRepository = moveInInspectionRepository;
        this.propertyService = propertyService;
    }

    public MoveInInspection create(Long propertyId, User tenant, String roomsJson) {
        Property property = propertyService.getPropertyById(propertyId);
        MoveInInspection inspection = new MoveInInspection();
        inspection.setProperty(property);
        inspection.setTenant(tenant);
        inspection.setLandlordName(property.getOwner() != null ? property.getOwner().getName() : null);
        inspection.setRoomsJson(roomsJson);
        inspection.setCreatedAt(LocalDateTime.now());
        return moveInInspectionRepository.save(inspection);
    }

    /**
     * Updates an in-progress inspection. Every field is optional (null
     * means "leave unchanged") so this doubles as the autosave endpoint
     * the wizard calls after every room, not just a one-time "complete"
     * action -- losing progress from a killed app is exactly the
     * failure mode this whole feature exists to fix.
     */
    public MoveInInspection update(Long id, User tenant, String roomsJson, String meterElectricity,
                                    String meterWater, String tenantSignature, String landlordSignature,
                                    boolean markComplete) {
        MoveInInspection inspection = getOwnedByTenant(id, tenant);
        if (roomsJson != null) inspection.setRoomsJson(roomsJson);
        if (meterElectricity != null) inspection.setMeterElectricity(meterElectricity);
        if (meterWater != null) inspection.setMeterWater(meterWater);
        if (tenantSignature != null) inspection.setTenantSignature(tenantSignature);
        if (landlordSignature != null) inspection.setLandlordSignature(landlordSignature);
        if (markComplete && inspection.getCompletedAt() == null) {
            inspection.setCompletedAt(LocalDateTime.now());
        }
        return moveInInspectionRepository.save(inspection);
    }

    private MoveInInspection getOwnedByTenant(Long id, User tenant) {
        return moveInInspectionRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> ApiException.notFound("Inspection not found"));
    }

    /**
     * Fetch for viewing -- allows the tenant who created it OR the
     * property's landlord, since the whole point of moving this
     * server-side was so landlords (and eventually admin, for dispute
     * resolution) can see it too, not just whoever's holding the phone
     * it was created on.
     */
    public MoveInInspection getViewable(Long id, User requester) {
        MoveInInspection inspection = moveInInspectionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Inspection not found"));
        boolean isTenant = inspection.getTenant().getId().equals(requester.getId());
        boolean isLandlord = inspection.getProperty().getOwner() != null
                && inspection.getProperty().getOwner().getId().equals(requester.getId());
        if (!isTenant && !isLandlord) {
            throw ApiException.forbidden("You don't have access to this inspection");
        }
        return inspection;
    }

    public List<MoveInInspection> findMine(User tenant) {
        return moveInInspectionRepository.findByTenantOrderByCreatedAtDesc(tenant);
    }

    public List<MoveInInspection> findForProperty(Long propertyId, User requester) {
        Property property = propertyService.getPropertyById(propertyId);
        boolean isLandlord = property.getOwner() != null && property.getOwner().getId().equals(requester.getId());
        if (!isLandlord) {
            throw ApiException.forbidden("Only the property owner can view its inspections");
        }
        return moveInInspectionRepository.findByPropertyOrderByCreatedAtDesc(property);
    }
}
