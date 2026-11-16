package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.IdentityStatus;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationAuditEvent;
import com.roost.model.VerificationDocument;
import com.roost.repository.UserRepository;
import com.roost.repository.VerificationAuditEventRepository;
import com.roost.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationDocumentServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private PrivateDocumentStorage storage;
    private VerificationDocumentRepository documents;
    private VerificationAuditEventRepository auditEvents;
    private UserRepository users;
    private RateLimiterService rateLimiter;
    private VerificationDocumentService service;
    private User lister;
    private final List<VerificationDocument> onFile = new ArrayList<>();

    @BeforeEach
    void setUp() {
        storage = mock(PrivateDocumentStorage.class);
        documents = mock(VerificationDocumentRepository.class);
        auditEvents = mock(VerificationAuditEventRepository.class);
        users = mock(UserRepository.class);
        rateLimiter = mock(RateLimiterService.class);
        service = new VerificationDocumentService(storage, documents, auditEvents, users, rateLimiter);

        lister = new User();
        lister.setId(7L);
        lister.setRole(Role.LANDLORD);
        when(users.findById(7L)).thenReturn(Optional.of(lister));
        when(storage.store(any(byte[].class), any(VerificationFilePolicy.Accepted.class)))
                .thenReturn("verification/11111111-1111-1111-1111-111111111111.jpg");
        when(documents.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(7L)).thenAnswer(i -> new ArrayList<>(onFile));
        when(documents.save(any(VerificationDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VerificationDocument onFileAlready(VerificationDocument.DocType type, VerificationDocument.Status status, String key) {
        VerificationDocument d = new VerificationDocument();
        d.setOwner(lister);
        d.setDocType(type);
        d.setStatus(status);
        d.setStorageKey(key);
        onFile.add(d);
        return d;
    }

    private void assertRejected(HttpStatus expected, Runnable action) {
        ApiException ex = assertThrows(ApiException.class, action::run);
        assertEquals(expected, ex.getStatus());
    }

    @Test
    @DisplayName("a valid file is stored privately and recorded with its type, size and checksum")
    void storesAndRecords() {
        VerificationDocument saved = service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_FRONT, JPEG);

        assertEquals("verification/11111111-1111-1111-1111-111111111111.jpg", saved.getStorageKey());
        assertEquals("image/jpeg", saved.getContentType());
        assertEquals(JPEG.length, saved.getSizeBytes());
        assertEquals(64, saved.getSha256().length());
        assertEquals(VerificationDocument.Status.SUBMITTED, saved.getStatus());
        assertEquals(lister, saved.getOwner());
    }

    @Test
    @DisplayName("submitting writes one SUBMITTED audit event")
    void auditsTheSubmission() {
        service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_FRONT, JPEG);

        ArgumentCaptor<VerificationAuditEvent> event = ArgumentCaptor.forClass(VerificationAuditEvent.class);
        verify(auditEvents).save(event.capture());
        assertEquals(VerificationAuditEvent.Action.SUBMITTED, event.getValue().getAction());
        assertEquals(lister, event.getValue().getActor());
        assertNotNull(event.getValue().getDocument());
    }

    @Test
    @DisplayName("only listers may submit")
    void tenantsAreRefused() {
        lister.setRole(Role.TENANT);

        assertRejected(HttpStatus.FORBIDDEN,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));
        verify(storage, never()).store(any(byte[].class), any(VerificationFilePolicy.Accepted.class));
    }

    @Test
    @DisplayName("property evidence types are not accepted here")
    void nonIdentityTypesAreRefused() {
        assertRejected(HttpStatus.BAD_REQUEST,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.TITLE_DEED, JPEG));
        assertRejected(HttpStatus.BAD_REQUEST, () -> service.submitIdentityDocument(lister, null, JPEG));
    }

    @Test
    @DisplayName("a file that is not an image or PDF is refused and nothing is stored")
    void badFilesAreRefused() {
        byte[] html = "<html></html>".getBytes(StandardCharsets.UTF_8);

        assertRejected(HttpStatus.BAD_REQUEST,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, html));
        verify(storage, never()).store(any(byte[].class), any(VerificationFilePolicy.Accepted.class));
        verify(documents, never()).save(any(VerificationDocument.class));
    }

    @Test
    @DisplayName("too many uploads in an hour are refused with 429 and nothing is stored")
    void rateLimited() {
        when(rateLimiter.isBlocked(anyString(), anyInt(), any(Duration.class))).thenReturn(true);

        assertRejected(HttpStatus.TOO_MANY_REQUESTS,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));
        verify(storage, never()).store(any(byte[].class), any(VerificationFilePolicy.Accepted.class));
    }

    @Test
    @DisplayName("every attempt counts toward the limit, as the key is recorded")
    void attemptsAreRecorded() {
        service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_FRONT, JPEG);

        verify(rateLimiter).record("verification-upload:7");
    }

    @Test
    @DisplayName("a verified identity can no longer be changed here")
    void verifiedIsLocked() {
        lister.setIdentityStatus(IdentityStatus.VERIFIED);

        assertRejected(HttpStatus.CONFLICT,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));
    }

    @Test
    @DisplayName("only the front of an ID leaves the status NOT_SUBMITTED")
    void frontAloneIsNotComplete() {
        service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_FRONT, JPEG);

        assertEquals(IdentityStatus.NOT_SUBMITTED, lister.getIdentityStatus());
    }

    @Test
    @DisplayName("front and back of an ID together move the lister to PENDING")
    void frontAndBackMakeItPending() {
        onFileAlready(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED,
                "verification/22222222-2222-2222-2222-222222222222.jpg");

        service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_BACK, JPEG);

        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
        verify(users).save(lister);
    }

    @Test
    @DisplayName("a passport alone is a complete set")
    void passportAloneIsComplete() {
        service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG);

        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
    }

    @Test
    @DisplayName("after a rejection, a new complete set returns the lister to PENDING")
    void rejectedCanResubmit() {
        lister.setIdentityStatus(IdentityStatus.REJECTED);

        service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG);

        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
    }

    @Test
    @DisplayName("sending the same type again replaces the earlier file and deletes it from storage")
    void replacesAnEarlierDocument() {
        String oldKey = "verification/33333333-3333-3333-3333-333333333333.jpg";
        VerificationDocument old = onFileAlready(VerificationDocument.DocType.NATIONAL_ID_FRONT,
                VerificationDocument.Status.REJECTED, oldKey);

        service.submitIdentityDocument(lister, VerificationDocument.DocType.NATIONAL_ID_FRONT, JPEG);

        assertNotNull(old.getDeletedAt());
        verify(storage).delete(oldKey);
        ArgumentCaptor<VerificationAuditEvent> events = ArgumentCaptor.forClass(VerificationAuditEvent.class);
        verify(auditEvents, org.mockito.Mockito.times(2)).save(events.capture());
        assertTrue(events.getAllValues().stream().anyMatch(e -> e.getAction() == VerificationAuditEvent.Action.DELETED));
    }

    @Test
    @DisplayName("an accepted document cannot be replaced, and nothing is stored")
    void acceptedCannotBeReplaced() {
        onFileAlready(VerificationDocument.DocType.PASSPORT, VerificationDocument.Status.ACCEPTED,
                "verification/44444444-4444-4444-4444-444444444444.jpg");

        assertRejected(HttpStatus.CONFLICT,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));
        verify(storage, never()).store(any(byte[].class), any(VerificationFilePolicy.Accepted.class));
    }

    @Test
    @DisplayName("if saving fails after the upload, the stored file is removed again")
    void failedSaveRemovesTheFile() {
        when(documents.save(any(VerificationDocument.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));

        verify(storage).delete("verification/11111111-1111-1111-1111-111111111111.jpg");
    }

    @Test
    @DisplayName("unavailable private storage surfaces as 503")
    void storageUnavailable() {
        when(storage.store(any(byte[].class), any(VerificationFilePolicy.Accepted.class)))
                .thenThrow(ApiException.serviceUnavailable("Document storage is not available right now."));

        assertRejected(HttpStatus.SERVICE_UNAVAILABLE,
                () -> service.submitIdentityDocument(lister, VerificationDocument.DocType.PASSPORT, JPEG));
        verify(documents, never()).save(any(VerificationDocument.class));
    }

    @Test
    @DisplayName("listing returns only identity documents")
    void listsIdentityDocumentsOnly() {
        onFileAlready(VerificationDocument.DocType.PASSPORT, VerificationDocument.Status.SUBMITTED,
                "verification/55555555-5555-5555-5555-555555555555.jpg");
        onFileAlready(VerificationDocument.DocType.TITLE_DEED, VerificationDocument.Status.SUBMITTED,
                "verification/66666666-6666-6666-6666-666666666666.pdf");

        List<VerificationDocument> listed = service.listIdentityDocuments(7L);

        assertEquals(1, listed.size());
        assertEquals(VerificationDocument.DocType.PASSPORT, listed.get(0).getDocType());
    }

    @Test
    @DisplayName("the identity status is read fresh from the database")
    void identityStatusIsReadFromTheDatabase() {
        lister.setIdentityStatus(IdentityStatus.REJECTED);

        assertEquals(IdentityStatus.REJECTED, service.getIdentityStatus(7L));
    }

    @Test
    @DisplayName("the identity status of an unknown user is a 404")
    void identityStatusOfUnknownUser() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertRejected(HttpStatus.NOT_FOUND, () -> service.getIdentityStatus(99L));
    }
}
