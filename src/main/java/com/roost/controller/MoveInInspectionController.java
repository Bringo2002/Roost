package com.roost.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roost.dto.MoveInInspectionResponseDto;
import com.roost.exception.ApiException;
import com.roost.model.MoveInInspection;
import com.roost.model.User;
import com.roost.service.MoveInInspectionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Server-side persistence for move-in inspections. See
 * MoveInInspectionService and MoveInInspection for why this exists --
 * the Flutter wizard previously had no save mechanism at all beyond a
 * clipboard copy.
 */
@RestController
@RequestMapping("/api/inspections")
public class MoveInInspectionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MoveInInspectionService moveInInspectionService;

    public MoveInInspectionController(MoveInInspectionService moveInInspectionService) {
        this.moveInInspectionService = moveInInspectionService;
    }

    @PostMapping
    public MoveInInspectionResponseDto create(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal User user) {
        requireAuth(user);
        Object propertyIdRaw = payload.get("propertyId");
        if (propertyIdRaw == null) {
            throw ApiException.badRequest("propertyId is required");
        }
        Long propertyId = Long.valueOf(propertyIdRaw.toString());
        String roomsJson = payload.get("rooms") != null ? toJsonString(payload.get("rooms")) : "[]";
        MoveInInspection inspection = moveInInspectionService.create(propertyId, user, roomsJson);
        return new MoveInInspectionResponseDto(inspection);
    }

    /**
     * Doubles as the autosave endpoint the wizard should call after
     * every room, not just once at the end -- every field is optional
     * (omit to leave unchanged), see MoveInInspectionService.update.
     */
    @PutMapping("/{id}")
    public MoveInInspectionResponseDto update(@PathVariable Long id, @RequestBody Map<String, Object> payload,
                                               @AuthenticationPrincipal User user) {
        requireAuth(user);
        String roomsJson = payload.get("rooms") != null ? toJsonString(payload.get("rooms")) : null;
        String meterElectricity = (String) payload.get("meterElectricity");
        String meterWater = (String) payload.get("meterWater");
        String tenantSignature = (String) payload.get("tenantSignature");
        String landlordSignature = (String) payload.get("landlordSignature");
        boolean markComplete = Boolean.TRUE.equals(payload.get("markComplete"));

        MoveInInspection inspection = moveInInspectionService.update(
                id, user, roomsJson, meterElectricity, meterWater, tenantSignature, landlordSignature, markComplete);
        return new MoveInInspectionResponseDto(inspection);
    }

    @GetMapping("/{id}")
    public MoveInInspectionResponseDto get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        requireAuth(user);
        return new MoveInInspectionResponseDto(moveInInspectionService.getViewable(id, user));
    }

    @GetMapping("/mine")
    public List<MoveInInspectionResponseDto> mine(@AuthenticationPrincipal User user) {
        requireAuth(user);
        return moveInInspectionService.findMine(user).stream().map(MoveInInspectionResponseDto::new).toList();
    }

    /** Landlord's view of every inspection filed for one of their
     *  properties -- ownership-checked in the service layer. */
    @GetMapping("/property/{propertyId}")
    public List<MoveInInspectionResponseDto> forProperty(@PathVariable Long propertyId, @AuthenticationPrincipal User user) {
        requireAuth(user);
        return moveInInspectionService.findForProperty(propertyId, user).stream().map(MoveInInspectionResponseDto::new).toList();
    }

    private void requireAuth(User user) {
        if (user == null) {
            throw ApiException.unauthorized("Please log in");
        }
    }

    private String toJsonString(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid rooms data");
        }
    }
}
