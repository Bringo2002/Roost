package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyDocumentReviewTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    private Property listingWith(List<String> documentUrls) {
        Property p = new Property();
        p.setId(1L);
        p.setDocumentUrls(new ArrayList<>(documentUrls));
        return p;
    }

    @Test
    @DisplayName("accepting documents marks the listing document-verified and saves it")
    void acceptMarksDocumentVerified() {
        Property p = listingWith(List.of("https://docs.example.com/deed.pdf"));
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        Property result = propertyService.acceptDocuments(1L);

        assertTrue(result.getDocumentVerified());
        verify(propertyRepository).save(p);
    }

    @Test
    @DisplayName("accepting twice is harmless")
    void acceptIsIdempotent() {
        Property p = listingWith(List.of("https://docs.example.com/deed.pdf"));
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.acceptDocuments(1L);
        Property second = propertyService.acceptDocuments(1L);

        assertTrue(second.getDocumentVerified());
    }

    @Test
    @DisplayName("a listing with no documents is refused with 400 and left unchanged")
    void acceptRefusesWhenNoDocuments() {
        Property p = listingWith(List.of());
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(p));

        ApiException ex = assertThrows(ApiException.class, () -> propertyService.acceptDocuments(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertFalse(Boolean.TRUE.equals(p.getDocumentVerified()));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("blank document entries do not count as documents")
    void acceptRefusesWhenOnlyBlankEntries() {
        Property p = listingWith(List.of("   "));
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(p));

        ApiException ex = assertThrows(ApiException.class, () -> propertyService.acceptDocuments(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("an unknown listing is a 404")
    void acceptUnknownListingIsNotFound() {
        when(propertyRepository.findById(9L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> propertyService.acceptDocuments(9L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("the pending queue is whatever the repository query returns")
    void pendingQueueDelegatesToRepository() {
        List<Property> pending = List.of(new Property());
        when(propertyRepository.findPendingDocumentReview()).thenReturn(pending);

        assertSame(pending, propertyService.getPendingDocumentReview());
    }
}
