package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the thin scheduled-task wrapper that delegates to
 * {@link AccountDeletionService#executeScheduledDeletions()}.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionScheduledTaskTest {

    @Mock
    private AccountDeletionService accountDeletionService;

    @InjectMocks
    private AccountDeletionScheduledTask task;

    @Test
    @DisplayName("delegates to AccountDeletionService.executeScheduledDeletions()")
    void delegatesToService() {
        when(accountDeletionService.executeScheduledDeletions()).thenReturn(3);

        task.processExpiredGracePeriods();

        verify(accountDeletionService).executeScheduledDeletions();
    }

    @Test
    @DisplayName("handles zero expired deletions without error")
    void handlesEmptyResultSet() {
        when(accountDeletionService.executeScheduledDeletions()).thenReturn(0);

        task.processExpiredGracePeriods();

        verify(accountDeletionService).executeScheduledDeletions();
    }
}
