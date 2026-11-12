package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import com.roost.repository.PropertyRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Self-service account deletion soft-deletes: personal data is wiped and the
 * account disabled, but the row stays so foreign keys held by messages,
 * reviews and applications remain valid.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountDeletionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPhone("+254700000000");
        user.setPhoneVerified(true);
        user.setEmailVerified(true);
        user.setPassword("stored-hash");
        user.setRole(Role.LANDLORD);
        user.setPublicKey("public-key");
        user.setDeviceToken("fcm-token");
        user.setAvatarUrl("https://cdn.example.com/ada.png");
        user.setSavedPropertyIds(new HashSet<>(Set.of(1L, 2L)));
    }

    private static Property propertyWithStatus(String status) {
        Property property = new Property();
        property.setStatus(status);
        return property;
    }

    @Test
    @DisplayName("wipes personal data, disables the account and invalidates the password")
    void anonymizesAndDisablesTheAccount() {
        when(propertyRepository.findByOwner(user)).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "ada@example.com");

        assertEquals("Deleted user", user.getName());
        assertEquals("deleted-42@deleted.roost.invalid", user.getEmail());
        assertNull(user.getPhone());
        assertFalse(user.isPhoneVerified());
        assertFalse(user.isEmailVerified());
        assertNull(user.getAvatarUrl());
        assertNull(user.getPublicKey());
        assertTrue(user.getSavedPropertyIds().isEmpty());
        assertEquals("unusable-hash", user.getPassword());
        assertFalse(user.isEnabled());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("unpublishes the owner's published listings and leaves the rest alone")
    void unpublishesPublishedListingsOnly() {
        Property published = propertyWithStatus("PUBLISHED");
        Property draft = propertyWithStatus("DRAFT");
        Property rented = propertyWithStatus("RENTED");
        when(propertyRepository.findByOwner(user)).thenReturn(List.of(published, draft, rented));
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "ada@example.com");

        assertEquals("DRAFT", published.getStatus());
        assertEquals("DRAFT", draft.getStatus());
        assertEquals("RENTED", rented.getStatus());
        verify(propertyRepository).saveAll(List.of(published, draft, rented));
    }

    @Test
    @DisplayName("accepts the confirmation email regardless of case and surrounding whitespace")
    void confirmationEmailIsNormalized() {
        when(propertyRepository.findByOwner(user)).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "  ADA@Example.com ");

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("rejects a missing or blank confirmation email without touching any data")
    void rejectsMissingConfirmation() {
        ApiException missing = assertThrows(ApiException.class, () -> service.deleteAccount(user, null));
        ApiException blank = assertThrows(ApiException.class, () -> service.deleteAccount(user, "  "));

        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatus());
        assertEquals("ada@example.com", user.getEmail());
        verify(userRepository, never()).save(any());
        verify(propertyRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("rejects a confirmation email that does not match the account")
    void rejectsMismatchedConfirmation() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.deleteAccount(user, "someone-else@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(user.isEnabled());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletes the applications the user submitted, which hold their ID and income details")
    void deletesTheUsersOwnApplications() {
        Application first = new Application();
        Application second = new Application();
        when(propertyRepository.findByOwner(user)).thenReturn(List.of());
        when(applicationRepository.findByApplicantOrderByCreatedAtDesc(user))
                .thenReturn(List.of(first, second));
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "ada@example.com");

        verify(applicationRepository).deleteAll(List.of(first, second));
    }

    @Test
    @DisplayName("deletes the applications other people sent to the user's listings")
    void deletesApplicationsReceivedOnTheUsersListings() {
        Property live = propertyWithStatus("PUBLISHED");
        Property draft = propertyWithStatus("DRAFT");
        when(propertyRepository.findByOwner(user)).thenReturn(List.of(live, draft));
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "ada@example.com");

        verify(applicationRepository).deleteByProperty(live);
        verify(applicationRepository).deleteByProperty(draft);
    }

    @Test
    @DisplayName("a rejected confirmation deletes no applications")
    void rejectedConfirmationDeletesNoApplications() {
        assertThrows(ApiException.class, () -> service.deleteAccount(user, "someone@else.com"));

        verifyNoInteractions(applicationRepository);
    }

    private static Property listingWithPersonalData(String status) {
        Property property = propertyWithStatus(status);
        property.setLandlordName("Ada Lovelace");
        property.setLandlordPhone("+254700000000");
        property.setLandlordId("42");
        property.setOwnerVerifyName("Ada L");
        property.setOwnerVerifyPhone("+254711111111");
        property.setCaretakerName("Charles");
        property.setCaretakerPhone("+254722222222");
        property.setEndorsementToken("endorse-me");
        property.setImageUrl("https://cdn.example.com/cover.jpg");
        property.setImageUrls(new ArrayList<>(List.of("https://cdn.example.com/a.jpg")));
        property.setDocumentUrls(new ArrayList<>(List.of("https://cdn.example.com/title-deed.pdf")));
        property.setVideoUrl("https://cdn.example.com/tour.mp4");
        return property;
    }

    @Test
    @DisplayName("strips contact details, the endorsement link and all media from every listing, whatever its status")
    void scrubsPersonalDataFromEveryListing() {
        Property published = listingWithPersonalData("PUBLISHED");
        Property rented = listingWithPersonalData("RENTED");
        when(propertyRepository.findByOwner(user)).thenReturn(List.of(published, rented));
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");

        service.deleteAccount(user, "ada@example.com");

        for (Property listing : List.of(published, rented)) {
            assertNull(listing.getLandlordName());
            assertNull(listing.getLandlordPhone());
            assertNull(listing.getLandlordId());
            assertNull(listing.getOwnerVerifyName());
            assertNull(listing.getOwnerVerifyPhone());
            assertNull(listing.getCaretakerName());
            assertNull(listing.getCaretakerPhone());
            assertNull(listing.getEndorsementToken());
            assertNull(listing.getImageUrl());
            assertTrue(listing.getImageUrls().isEmpty());
            assertTrue(listing.getDocumentUrls().isEmpty());
            assertNull(listing.getVideoUrl());
        }
        verify(propertyRepository).saveAll(List.of(published, rented));
    }

    @Test
    @DisplayName("a rejected confirmation leaves the listings' personal data untouched")
    void rejectedConfirmationLeavesListingsUntouched() {
        assertThrows(ApiException.class, () -> service.deleteAccount(user, "someone@else.com"));

        verifyNoInteractions(propertyRepository);
    }
}
