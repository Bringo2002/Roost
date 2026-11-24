package com.roost.service;

import com.roost.dto.BlockedUserDto;
import com.roost.dto.ReportUserRequestDto;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.UserBlock;
import com.roost.model.UserReport;
import com.roost.model.UserReport.Reason;
import com.roost.repository.UserBlockRepository;
import com.roost.repository.UserReportRepository;
import com.roost.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Business rules for blocking and reporting other users. */
@ExtendWith(MockitoExtension.class)
class UserSafetyServiceTest {

    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserReportRepository userReportRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserSafetyService service;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = user(1L, "Me");
        other = user(2L, "Other");
    }

    private static User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole(Role.TENANT);
        return u;
    }

    private static ReportUserRequestDto report(Reason reason, String note) {
        ReportUserRequestDto dto = new ReportUserRequestDto();
        dto.setReason(reason);
        dto.setNote(note);
        return dto;
    }

    // -- block ----------------------------------------------------------

    @Test
    @DisplayName("blocking saves a block from me to them")
    void blockSavesTheBlock() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userBlockRepository.existsByBlockerAndBlocked(me, other)).thenReturn(false);

        service.blockUser(me, 2L);

        ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
        verify(userBlockRepository).save(saved.capture());
        assertEquals(me, saved.getValue().getBlocker());
        assertEquals(other, saved.getValue().getBlocked());
    }

    @Test
    @DisplayName("blocking someone already blocked does nothing and does not fail")
    void blockingTwiceIsANoOp() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userBlockRepository.existsByBlockerAndBlocked(me, other)).thenReturn(true);

        service.blockUser(me, 2L);

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("you cannot block yourself")
    void cannotBlockYourself() {
        ApiException ex = assertThrows(ApiException.class, () -> service.blockUser(me, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("blocking a user that does not exist is a 404")
    void blockingAnUnknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.blockUser(me, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(userBlockRepository, never()).save(any());
    }

    // -- unblock --------------------------------------------------------

    @Test
    @DisplayName("unblocking removes the block")
    void unblockRemovesTheBlock() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));

        service.unblockUser(me, 2L);

        verify(userBlockRepository).deleteByPair(me, other);
    }

    @Test
    @DisplayName("unblocking a user that does not exist is a 404")
    void unblockingAnUnknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.unblockUser(me, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // -- blocked list ---------------------------------------------------

    @Test
    @DisplayName("the blocked list shows each blocked person's id, name, photo and when they were blocked")
    void blockedListMapsToDtos() {
        other.setAvatarUrl("https://cdn.example/a.jpg");
        UserBlock block = new UserBlock(me, other);
        LocalDateTime blockedAt = LocalDateTime.of(2026, 11, 20, 10, 0);
        block.setCreatedAt(blockedAt);
        when(userBlockRepository.findWithBlockedByBlocker(me)).thenReturn(List.of(block));

        List<BlockedUserDto> list = service.getBlockedUsers(me);

        assertEquals(1, list.size());
        assertEquals(2L, list.get(0).getId());
        assertEquals("Other", list.get(0).getName());
        assertEquals("https://cdn.example/a.jpg", list.get(0).getAvatarUrl());
        assertEquals(blockedAt, list.get(0).getBlockedAt());
    }

    @Test
    @DisplayName("the blocked list is empty when nobody is blocked")
    void blockedListIsEmpty() {
        when(userBlockRepository.findWithBlockedByBlocker(me)).thenReturn(List.of());

        assertTrue(service.getBlockedUsers(me).isEmpty());
    }

    // -- report ---------------------------------------------------------

    @Test
    @DisplayName("a report is saved with the reason and the trimmed note")
    void reportIsSaved() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userReportRepository.countByReporterAndReportedAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        service.reportUser(me, 2L, report(Reason.HARASSMENT, "  keeps insulting me  "));

        ArgumentCaptor<UserReport> saved = ArgumentCaptor.forClass(UserReport.class);
        verify(userReportRepository).save(saved.capture());
        assertEquals(me, saved.getValue().getReporter());
        assertEquals(other, saved.getValue().getReported());
        assertEquals(Reason.HARASSMENT, saved.getValue().getReason());
        assertEquals("keeps insulting me", saved.getValue().getNote());
    }

    @Test
    @DisplayName("a blank note is stored as no note")
    void blankNoteBecomesNull() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userReportRepository.countByReporterAndReportedAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        service.reportUser(me, 2L, report(Reason.SPAM, "   "));

        ArgumentCaptor<UserReport> saved = ArgumentCaptor.forClass(UserReport.class);
        verify(userReportRepository).save(saved.capture());
        assertNull(saved.getValue().getNote());
    }

    @Test
    @DisplayName("a note over the limit is rejected")
    void overlongNoteIsRejected() {
        String tooLong = "x".repeat(UserReport.MAX_NOTE_LENGTH + 1);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.reportUser(me, 2L, report(Reason.OTHER, tooLong)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("a note of exactly the limit is accepted")
    void noteAtTheLimitIsAccepted() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userReportRepository.countByReporterAndReportedAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);

        service.reportUser(me, 2L, report(Reason.OTHER, "x".repeat(UserReport.MAX_NOTE_LENGTH)));

        verify(userReportRepository).save(any());
    }

    @Test
    @DisplayName("a report needs a reason")
    void reasonIsRequired() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.reportUser(me, 2L, report(null, "note")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("you cannot report yourself")
    void cannotReportYourself() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.reportUser(me, 1L, report(Reason.SPAM, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("reporting a user that does not exist is a 404")
    void reportingAnUnknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.reportUser(me, 99L, report(Reason.SPAM, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("reporting the same user again within a day is refused as a duplicate")
    void duplicateReportInsideTheWindowIsRefused() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userReportRepository.countByReporterAndReportedAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.reportUser(me, 2L, report(Reason.SPAM, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("the duplicate check looks back 24 hours")
    void duplicateWindowIsTwentyFourHours() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(userReportRepository.countByReporterAndReportedAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        LocalDateTime before = LocalDateTime.now();

        service.reportUser(me, 2L, report(Reason.SPAM, null));

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userReportRepository).countByReporterAndReportedAndCreatedAtAfter(any(), any(), since.capture());
        Duration lookBack = Duration.between(since.getValue(), before);
        assertTrue(lookBack.compareTo(Duration.ofHours(24).minusSeconds(5)) >= 0
                && lookBack.compareTo(Duration.ofHours(24).plusSeconds(5)) <= 0,
                "look-back was " + lookBack);
    }
}
