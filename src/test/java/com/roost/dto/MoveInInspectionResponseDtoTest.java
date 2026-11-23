package com.roost.dto;

import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveInInspectionResponseDtoTest {

    private MoveInInspection inspection(String accountName, String recordedName) {
        User tenant = new User();
        tenant.setName(accountName);
        Property property = new Property();
        property.setTitle("2BR in Kilimani");
        MoveInInspection inspection = new MoveInInspection();
        inspection.setProperty(property);
        inspection.setTenant(tenant);
        inspection.setTenantName(recordedName);
        inspection.setRoomsJson("[]");
        inspection.setCreatedAt(LocalDateTime.of(2026, 12, 1, 9, 0));
        return inspection;
    }

    @Test
    @DisplayName("the recorded tenant name is shown even after the account was renamed")
    void showsTheRecordedNameAfterTheAccountIsErased() {
        MoveInInspectionResponseDto dto =
                new MoveInInspectionResponseDto(inspection("Deleted user", "Ada Lovelace"));

        assertEquals("Ada Lovelace", dto.getTenantName());
    }

    @Test
    @DisplayName("an inspection with no recorded name falls back to the account's name")
    void fallsBackToTheAccountNameForOlderRecords() {
        MoveInInspectionResponseDto dto =
                new MoveInInspectionResponseDto(inspection("Grace Hopper", null));

        assertEquals("Grace Hopper", dto.getTenantName());
    }

    @Test
    @DisplayName("a blank recorded name falls back to the account's name")
    void fallsBackWhenTheRecordedNameIsBlank() {
        MoveInInspectionResponseDto dto =
                new MoveInInspectionResponseDto(inspection("Grace Hopper", "  "));

        assertEquals("Grace Hopper", dto.getTenantName());
    }
}
