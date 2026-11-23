package com.roost.service;

import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.MoveInInspectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoveInInspectionServiceTest {

    @Mock private MoveInInspectionRepository moveInInspectionRepository;
    @Mock private PropertyService propertyService;

    @InjectMocks
    private MoveInInspectionService service;

    @Test
    @DisplayName("create records the tenant's name on the inspection")
    void createRecordsTheTenantName() {
        User landlord = new User();
        landlord.setName("Grace Hopper");
        Property property = new Property();
        property.setOwner(landlord);
        User tenant = new User();
        tenant.setName("Ada Lovelace");
        when(propertyService.getPropertyById(5L)).thenReturn(property);
        when(moveInInspectionRepository.save(any(MoveInInspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MoveInInspection created = service.create(5L, tenant, "[]");

        assertEquals("Ada Lovelace", created.getTenantName());
        assertEquals("Grace Hopper", created.getLandlordName());
    }
}
