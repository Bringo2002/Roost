package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRetentionScheduledTaskTest {

    @Mock private InspectionRetentionService retentionService;

    @InjectMocks
    private InspectionRetentionScheduledTask task;

    @Test
    @DisplayName("the nightly run asks the retention service to delete expired inspections")
    void nightlyRunDeletesExpiredInspections() {
        when(retentionService.deleteExpired()).thenReturn(3);

        task.deleteExpiredInspections();

        verify(retentionService).deleteExpired();
    }
}
