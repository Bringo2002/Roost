package com.roost.controller;

import com.roost.dto.MyListingsPageResponse;
import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyV2ControllerTest {

    @Mock
    private PropertyService propertyService;

    private PropertyV2Controller controller;
    private User landlord;

    @BeforeEach
    void setUp() {
        controller = new PropertyV2Controller(propertyService);
        landlord = new User();
        landlord.setRole(Role.LANDLORD);
    }

    private PropertyService.MyListingsPage page(int number, int size, boolean hasNext) {
        return new PropertyService.MyListingsPage(
                new SliceImpl<>(List.of(new Property()), PageRequest.of(number, size), hasNext), null);
    }

    @Test
    @DisplayName("unauthenticated -> 401, service never called")
    void unauthenticatedIs401() {
        ResponseEntity<?> res = controller.getMyListings(null, "ALL", 0, 20);

        assertEquals(401, res.getStatusCode().value());
        verify(propertyService, never()).getMyListingsPage(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("non-landlord -> 403, service never called")
    void tenantIs403() {
        User tenant = new User();
        tenant.setRole(Role.TENANT);

        ResponseEntity<?> res = controller.getMyListings(tenant, "ALL", 0, 20);

        assertEquals(403, res.getStatusCode().value());
        verify(propertyService, never()).getMyListingsPage(any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("landlord gets 200 with the envelope built from the service result")
    void landlordGetsEnvelope() {
        when(propertyService.getMyListingsPage(landlord, "DRAFT", 1, 10)).thenReturn(page(1, 10, true));

        ResponseEntity<?> res = controller.getMyListings(landlord, "DRAFT", 1, 10);

        assertEquals(200, res.getStatusCode().value());
        MyListingsPageResponse body = assertInstanceOf(MyListingsPageResponse.class, res.getBody());
        assertEquals(1, body.page());
        assertEquals(10, body.size());
        assertEquals(true, body.hasNext());
        assertEquals(1, body.items().size());
    }

    @Test
    @DisplayName("missing page/size default to page 0, size 20")
    void defaults() {
        when(propertyService.getMyListingsPage(landlord, "ALL", 0, 20)).thenReturn(page(0, 20, false));

        controller.getMyListings(landlord, "ALL", null, null);

        verify(propertyService).getMyListingsPage(landlord, "ALL", 0, 20);
    }

    @Test
    @DisplayName("size is clamped to 1..50 and a negative page becomes 0")
    void clampsPageAndSize() {
        when(propertyService.getMyListingsPage(eq(landlord), anyString(), anyInt(), anyInt()))
                .thenReturn(page(0, 50, false));

        controller.getMyListings(landlord, "ALL", -5, 10_000);
        verify(propertyService).getMyListingsPage(landlord, "ALL", 0, 50);

        controller.getMyListings(landlord, "ALL", 3, 0);
        verify(propertyService).getMyListingsPage(landlord, "ALL", 3, 1);
    }

    @Test
    @DisplayName("an invalid filter's ApiException propagates to GlobalExceptionHandler (400)")
    void invalidFilterPropagates() {
        when(propertyService.getMyListingsPage(landlord, "NOPE", 0, 20))
                .thenThrow(ApiException.badRequest("Unknown filter 'NOPE'"));

        assertThrows(ApiException.class, () -> controller.getMyListings(landlord, "NOPE", 0, 20));
    }
}
