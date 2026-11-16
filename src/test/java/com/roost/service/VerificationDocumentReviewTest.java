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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The reviewer's side of identity verification: view a file, accept, reject. */
class VerificationDocumentReviewTest {

    private static final String KEY = "verification/11111111-1111-1111-1111-111111111111.jpg";
    private static final byte[] BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    private PrivateDocumentStorage storage;
    private VerificationDocumentRepository documents;
    private VerificationAuditEventRepository auditEvents;
    private UserRepository users;
    private VerificationDocumentService service;
    private User admin;
    private User lister;

    @BeforeEach
    void setUp() {
        storage = mock(PrivateDocumentStorage.class);
        documents = mock(VerificationDocumentRepository.class);
        auditEvents = mock(VerificationAuditEventRepository.class);
        users = mock(UserRepository.class);
        service = new VerificationDocumentService(storage, documents, auditEvents, users, mock(RateLimiterService.class));

        admin = new User();
        admin.setId(99L);
        admin.setRole(Role.ADMIN);

        lister = new User();
        lister.setId(7L);
        lister.setRole(Role.LANDLORD);
        lister.setIdentityStatus(IdentityStatus.PENDING);
        when(users.findById(7L)).thenReturn(Optional.of(lister));
        when(documents.save(any(VerificationDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VerificationDocument doc(VerificationDocument.DocType type, VerificationDocument.Status status) {
        VerificationDocument d = new VerificationDocument();
        d.setOwner(lister);
        d.setDocType(type);
        d.setStatus(status);
        d.setStorageKey(KEY);
        d.setContentType("image/jpeg");
        return d;
    }

    private void onFile(VerificationDocument... docs) {
        when(documents.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(7L)).thenReturn(List.of(docs));
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ApiException ex = assertThrows(ApiException.class, action::run);
        assertEquals(expected, ex.getStatus());
    }

    private List<VerificationAuditEvent> savedEvents(int times) {
        ArgumentCaptor<VerificationAuditEvent> captor = ArgumentCaptor.forClass(VerificationAuditEvent.class);
        verify(auditEvents, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    // ── viewing a file ───────────────────────────────────────────────────

    @Test
    @DisplayName("viewing returns the file and records who looked")
    void viewReturnsFileAndRecordsView() {
        VerificationDocument d = doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED);
        when(documents.findById(5L)).thenReturn(Optional.of(d));
        when(storage.load(KEY)).thenReturn(BYTES);

        VerificationDocumentService.ReviewFile file = service.viewDocument(admin, 5L);

        assertArrayEquals(BYTES, file.data());
        assertEquals("image/jpeg", file.contentType());
        VerificationAuditEvent event = savedEvents(1).get(0);
        assertEquals(VerificationAuditEvent.Action.VIEWED, event.getAction());
        assertSame(admin, event.getActor());
        assertSame(d, event.getDocument());
    }

    @Test
    @DisplayName("a deleted document can't be opened")
    void viewRefusesDeletedDocument() {
        VerificationDocument d = doc(VerificationDocument.DocType.PASSPORT, VerificationDocument.Status.SUBMITTED);
        d.setDeletedAt(LocalDateTime.now());
        when(documents.findById(5L)).thenReturn(Optional.of(d));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.viewDocument(admin, 5L));
        verify(storage, never()).load(anyString());
        verify(auditEvents, never()).save(any(VerificationAuditEvent.class));
    }

    @Test
    @DisplayName("an unknown document is a 404")
    void viewUnknownDocumentIsNotFound() {
        when(documents.findById(5L)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> service.viewDocument(admin, 5L));
    }

    @Test
    @DisplayName("if the file can't be read, no view is recorded")
    void viewRecordsNothingWhenStorageFails() {
        VerificationDocument d = doc(VerificationDocument.DocType.PASSPORT, VerificationDocument.Status.SUBMITTED);
        when(documents.findById(5L)).thenReturn(Optional.of(d));
        when(storage.load(KEY)).thenThrow(ApiException.serviceUnavailable("Document storage is not available right now."));

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.viewDocument(admin, 5L));
        verify(auditEvents, never()).save(any(VerificationAuditEvent.class));
    }

    // ── accepting ────────────────────────────────────────────────────────

    @Test
    @DisplayName("accepting marks every document accepted, records the reviewer and verifies the lister")
    void acceptVerifiesTheLister() {
        VerificationDocument front = doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED);
        VerificationDocument back = doc(VerificationDocument.DocType.NATIONAL_ID_BACK, VerificationDocument.Status.SUBMITTED);
        onFile(front, back);

        service.acceptIdentity(admin, 7L);

        for (VerificationDocument d : List.of(front, back)) {
            assertEquals(VerificationDocument.Status.ACCEPTED, d.getStatus());
            assertSame(admin, d.getReviewedBy());
            assertNotNull(d.getReviewedAt());
            assertNull(d.getRejectReason());
        }
        assertEquals(IdentityStatus.VERIFIED, lister.getIdentityStatus());
        verify(users).save(lister);
        List<VerificationAuditEvent> events = savedEvents(2);
        assertEquals(VerificationAuditEvent.Action.ACCEPTED, events.get(0).getAction());
        assertEquals(VerificationAuditEvent.Action.ACCEPTED, events.get(1).getAction());
    }

    @Test
    @DisplayName("a lister who is not waiting for review can't be accepted")
    void acceptRequiresPending() {
        lister.setIdentityStatus(IdentityStatus.VERIFIED);

        assertStatus(HttpStatus.CONFLICT, () -> service.acceptIdentity(admin, 7L));
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an admin can't accept their own documents")
    void acceptRefusesOwnDocuments() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.acceptIdentity(lister, 7L));
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("accepting an unknown user is a 404")
    void acceptUnknownUserIsNotFound() {
        when(users.findById(8L)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> service.acceptIdentity(admin, 8L));
    }

    @Test
    @DisplayName("an incomplete set can't be accepted")
    void acceptRefusesIncompleteSet() {
        onFile(doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED));

        assertStatus(HttpStatus.CONFLICT, () -> service.acceptIdentity(admin, 7L));
        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a document rejected earlier and not replaced blocks acceptance")
    void acceptRefusesEarlierRejection() {
        VerificationDocument front = doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED);
        VerificationDocument back = doc(VerificationDocument.DocType.NATIONAL_ID_BACK, VerificationDocument.Status.REJECTED);
        onFile(front, back);

        assertStatus(HttpStatus.CONFLICT, () -> service.acceptIdentity(admin, 7L));
        assertEquals(VerificationDocument.Status.REJECTED, back.getStatus());
        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
        verify(documents, never()).save(any(VerificationDocument.class));
    }

    // ── rejecting ────────────────────────────────────────────────────────

    @Test
    @DisplayName("rejecting marks the submitted documents rejected with the reason and updates the lister")
    void rejectRecordsReasonAndStatus() {
        VerificationDocument front = doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.SUBMITTED);
        VerificationDocument back = doc(VerificationDocument.DocType.NATIONAL_ID_BACK, VerificationDocument.Status.SUBMITTED);
        onFile(front, back);

        service.rejectIdentity(admin, 7L, "  The photo is blurry.  ");

        for (VerificationDocument d : List.of(front, back)) {
            assertEquals(VerificationDocument.Status.REJECTED, d.getStatus());
            assertEquals("The photo is blurry.", d.getRejectReason());
            assertSame(admin, d.getReviewedBy());
            assertNotNull(d.getReviewedAt());
        }
        assertEquals(IdentityStatus.REJECTED, lister.getIdentityStatus());
        List<VerificationAuditEvent> events = savedEvents(2);
        assertEquals(VerificationAuditEvent.Action.REJECTED, events.get(0).getAction());
        assertEquals("The photo is blurry.", events.get(0).getNote());
    }

    @Test
    @DisplayName("rejecting leaves an earlier rejection and its reason untouched")
    void rejectKeepsEarlierReason() {
        VerificationDocument front = doc(VerificationDocument.DocType.NATIONAL_ID_FRONT, VerificationDocument.Status.REJECTED);
        front.setRejectReason("Cropped at the edge.");
        VerificationDocument back = doc(VerificationDocument.DocType.NATIONAL_ID_BACK, VerificationDocument.Status.SUBMITTED);
        onFile(front, back);

        service.rejectIdentity(admin, 7L, "Not readable.");

        assertEquals("Cropped at the edge.", front.getRejectReason());
        assertEquals("Not readable.", back.getRejectReason());
        savedEvents(1);
    }

    @Test
    @DisplayName("a rejection needs a reason")
    void rejectRequiresReason() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.rejectIdentity(admin, 7L, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.rejectIdentity(admin, 7L, "   "));
        assertEquals(IdentityStatus.PENDING, lister.getIdentityStatus());
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a reason longer than 1000 characters is refused")
    void rejectRefusesTooLongReason() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.rejectIdentity(admin, 7L, "x".repeat(1001)));
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a lister who is not waiting for review can't be rejected")
    void rejectRequiresPending() {
        lister.setIdentityStatus(IdentityStatus.NOT_SUBMITTED);

        assertStatus(HttpStatus.CONFLICT, () -> service.rejectIdentity(admin, 7L, "Not readable."));
        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an admin can't reject their own documents")
    void rejectRefusesOwnDocuments() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.rejectIdentity(lister, 7L, "Not readable."));
        verify(users, never()).save(any(User.class));
    }

    // ── the queue ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the pending list is the listers at the pending stage")
    void pendingListComesFromUserRepository() {
        List<User> pending = List.of(lister);
        when(users.findByIdentityStatusOrderByIdAsc(IdentityStatus.PENDING)).thenReturn(pending);

        assertSame(pending, service.listPendingIdentities());
    }
}
