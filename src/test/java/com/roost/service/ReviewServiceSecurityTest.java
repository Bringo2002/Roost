package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security test for {@link ReviewService}: verifies that a landlord cannot
 * review their own listing. Written Red-first.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceSecurityTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private PropertyService propertyService;

    @InjectMocks
    private ReviewService reviewService;

    private User landlord;
    private Property ownedProperty;

    @BeforeEach
    void setUp() {
        landlord = new User();
        landlord.setId(1L);
        landlord.setName("Landlord Lee");

        ownedProperty = new Property();
        ownedProperty.setId(10L);
        ownedProperty.setOwner(landlord);
    }

    @Test
    @DisplayName("submitReview rejects a landlord reviewing their own listing")
    void submitReview_rejectsSelfReview() {
        when(propertyService.getPropertyById(10L)).thenReturn(ownedProperty);

        Map<String, Object> payload = Map.of(
                "propertyId", "10",
                "rating", "5",
                "comment", "Great place!"
        );

        ApiException ex = assertThrows(ApiException.class,
                () -> reviewService.submitReview(landlord, payload));
        assertTrue(ex.getMessage().toLowerCase().contains("own"),
                "Error message should mention reviewing own listing: " + ex.getMessage());
    }
}
