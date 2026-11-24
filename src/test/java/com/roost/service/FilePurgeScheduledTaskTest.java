package com.roost.service;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilePurgeScheduledTaskTest {

    @Mock private FilePurgeService filePurgeService;

    @InjectMocks
    private FilePurgeScheduledTask task;

    private PendingFileDeletion entry(String key) {
        return new PendingFileDeletion(Bucket.PUBLIC, key, LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("every due file is handed to the purge service")
    void everyDueFileIsPurged() {
        PendingFileDeletion first = entry("a.jpg");
        PendingFileDeletion second = entry("b.jpg");
        when(filePurgeService.findDue()).thenReturn(List.of(first, second));
        when(filePurgeService.purge(first)).thenReturn(FilePurgeService.Outcome.DELETED);
        when(filePurgeService.purge(second)).thenReturn(FilePurgeService.Outcome.KEPT_IN_USE);

        task.purgeDueFiles();

        verify(filePurgeService).purge(first);
        verify(filePurgeService).purge(second);
    }

    @Test
    @DisplayName("a file that fails does not stop the ones after it")
    void aFailureDoesNotStopTheRest() {
        PendingFileDeletion first = entry("a.jpg");
        PendingFileDeletion broken = entry("broken.jpg");
        PendingFileDeletion last = entry("c.jpg");
        when(filePurgeService.findDue()).thenReturn(List.of(first, broken, last));
        when(filePurgeService.purge(first)).thenReturn(FilePurgeService.Outcome.DELETED);
        when(filePurgeService.purge(broken)).thenThrow(new RuntimeException("storage unavailable"));
        when(filePurgeService.purge(last)).thenReturn(FilePurgeService.Outcome.DELETED);

        task.purgeDueFiles();

        verify(filePurgeService).purge(first);
        verify(filePurgeService).purge(broken);
        verify(filePurgeService).purge(last);
    }

    @Test
    @DisplayName("nothing is purged when nothing is due")
    void nothingDueNothingPurged() {
        when(filePurgeService.findDue()).thenReturn(List.of());

        task.purgeDueFiles();

        verify(filePurgeService, never()).purge(org.mockito.ArgumentMatchers.any());
    }
}
