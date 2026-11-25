package com.roost.service;

import com.roost.model.MoveInInspection;
import com.roost.repository.MoveInInspectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRetentionServiceTest {

    @Mock private MoveInInspectionRepository inspectionRepository;

    @InjectMocks
    private InspectionRetentionService service;

    @Test
    @DisplayName("inspections older than 24 months are deleted and counted")
    void expiredInspectionsAreDeleted() {
        List<MoveInInspection> expired = List.of(new MoveInInspection(), new MoveInInspection());
        when(inspectionRepository.findExpired(any(LocalDateTime.class))).thenReturn(expired);

        int deleted = service.deleteExpired();

        assertEquals(2, deleted);
        verify(inspectionRepository).deleteAll(expired);
    }

    @Test
    @DisplayName("the cutoff is 24 months before now")
    void cutoffIsTwentyFourMonthsAgo() {
        when(inspectionRepository.findExpired(any(LocalDateTime.class))).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        service.deleteExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(inspectionRepository).findExpired(captor.capture());
        LocalDateTime cutoff = captor.getValue();
        assertFalse(cutoff.isBefore(before.minusMonths(24)));
        assertTrue(cutoff.isBefore(LocalDateTime.now().minusMonths(24).plusSeconds(1)));
    }

    @Test
    @DisplayName("nothing is deleted when nothing has expired")
    void nothingExpiredNothingDeleted() {
        when(inspectionRepository.findExpired(any(LocalDateTime.class))).thenReturn(List.of());

        assertEquals(0, service.deleteExpired());

        verify(inspectionRepository, never()).deleteAll(any());
    }
}
