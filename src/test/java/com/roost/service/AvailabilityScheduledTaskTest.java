package com.roost.service;

import com.roost.model.NotificationType;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AvailabilityScheduledTask}: reminders are sent for
 * listings overdue by 7+ days, and unconfirmed listings are expired after
 * the 24h grace period. Each iteration is resilient to individual failures.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityScheduledTaskTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private FirebasePushService firebasePushService;

    @InjectMocks
    private AvailabilityScheduledTask task;

    private User landlord;

    @BeforeEach
    void setUp() {
        landlord = new User();
        landlord.setId(1L);
        landlord.setName("Landlord");
        landlord.setRole(Role.LANDLORD);
        landlord.setEnabled(true);
        landlord.setDeviceToken("fcm-token");
    }

    private Property listing(Long id, String title) {
        Property p = new Property();
        p.setId(id);
        p.setTitle(title);
        p.setAvailable(true);
        p.setOwner(landlord);
        p.setLastConfirmedAt(LocalDateTime.now().minusDays(10));
        return p;
    }

    @Test
    @DisplayName("sends reminders for listings due >7 days and saves all")
    void sendsReminders() {
        Property p1 = listing(1L, "Studio in CBD");
        Property p2 = listing(2L, "2BR Westlands");
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(any()))
                .thenReturn(List.of(p1, p2));
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(any()))
                .thenReturn(List.of());

        task.checkExpiredListings();

        verify(firebasePushService, times(2)).sendToUser(eq(landlord), anyString(), anyString(), any());
        verify(propertyRepository).saveAll(List.of(p1, p2));
    }

    @Test
    @DisplayName("expires unconfirmed listings after 24h grace period")
    void expiresUnconfirmed() {
        Property expired = listing(3L, "Penthouse");
        expired.setRemindedAt(LocalDateTime.now().minusHours(30));
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(any()))
                .thenReturn(List.of());
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(any()))
                .thenReturn(List.of(expired));

        task.checkExpiredListings();

        // Property should be marked unavailable
        verify(propertyRepository).saveAll(List.of(expired));
    }

    @Test
    @DisplayName("no-op when nothing is due")
    void noOpWhenNothingDue() {
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(any()))
                .thenReturn(List.of());
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(any()))
                .thenReturn(List.of());

        task.checkExpiredListings();

        verify(propertyRepository, never()).saveAll(anyList());
        verify(firebasePushService, never()).sendToUser(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("listing with null owner skips push but still gets remindedAt set")
    void nullOwnerSkipsPush() {
        Property p = listing(4L, "Orphan listing");
        p.setOwner(null);
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNullAndLastConfirmedAtBefore(any()))
                .thenReturn(List.of(p));
        when(propertyRepository.findByAvailableTrueAndRemindedAtIsNotNullAndRemindedAtBefore(any()))
                .thenReturn(List.of());

        task.checkExpiredListings();

        verify(firebasePushService, never()).sendToUser(any(), anyString(), anyString(), any());
        verify(propertyRepository).saveAll(List.of(p));
    }
}
