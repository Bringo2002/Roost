package com.roost.repository;

import com.roost.model.AuthorityClaim;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationAuditEvent;
import com.roost.model.VerificationDocument;
import com.roost.util.EmailNormalizer;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the V6 schema (verification documents, authority claims, audit
 * trail) against a real PostgreSQL, so the rules that live in the database
 * (one open claim per listing, documents tied to the right scope, an
 * append-only audit trail) are tested where they are enforced.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class VerificationRepositoriesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private AuthorityClaimRepository claims;

    @Autowired
    private VerificationDocumentRepository documents;

    @Autowired
    private VerificationAuditEventRepository auditEvents;

    @Autowired
    private TestEntityManager em;

    private User lister;
    private User admin;
    private Property listing;

    @BeforeEach
    void setUp() {
        lister = persistUser("lister", Role.LANDLORD);
        admin = persistUser("admin", Role.ADMIN);
        listing = persistListing();
    }

    private User persistUser(String prefix, Role role) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(role);
        return em.persist(u);
    }

    private Property persistListing() {
        Property p = new Property();
        p.setTitle("Flat");
        p.setDescription("desc");
        p.setLocation("Nairobi");
        p.setPrice(10000);
        p.setBedrooms(1);
        p.setType("RENTAL");
        p.setLandlordPhone("+254700000000");
        p.setStatus("PUBLISHED");
        p.setAvailable(true);
        p.setOwner(lister);
        return em.persist(p);
    }

    private AuthorityClaim claim(AuthorityClaim.ClaimType type, String ownerName) {
        AuthorityClaim c = new AuthorityClaim();
        c.setProperty(listing);
        c.setClaimant(lister);
        c.setClaimType(type);
        c.setOwnerName(ownerName);
        return c;
    }

    private VerificationDocument document(VerificationDocument.DocType type, AuthorityClaim claim) {
        VerificationDocument d = new VerificationDocument();
        d.setOwner(lister);
        d.setClaim(claim);
        d.setDocType(type);
        d.setStorageKey("verification/" + UUID.randomUUID() + ".jpg");
        d.setContentType("image/jpeg");
        d.setSizeBytes(1234);
        d.setSha256("a".repeat(64));
        return d;
    }

    @Test
    @DisplayName("a new claim starts PENDING and is found among the listing's open claims")
    void newClaimIsPendingAndOpen() {
        AuthorityClaim saved = em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));

        assertEquals(AuthorityClaim.Status.PENDING, saved.getStatus());
        assertTrue(saved.getSubmittedAt() != null);
        List<AuthorityClaim> open = claims.findByPropertyIdAndStatusIn(
                listing.getId(), List.of(AuthorityClaim.Status.PENDING, AuthorityClaim.Status.CONFIRMED));
        assertEquals(1, open.size());
    }

    @Test
    @DisplayName("a second open claim on the same listing is refused by the database")
    void secondOpenClaimIsRefused() {
        em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null)));
    }

    @Test
    @DisplayName("once a claim is rejected, a new claim can be made for the listing")
    void rejectedClaimDoesNotBlockANewOne() {
        AuthorityClaim first = em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));
        first.setStatus(AuthorityClaim.Status.REJECTED);
        first.setDecidedAt(LocalDateTime.now());
        first.setDecidedBy(admin);
        em.flush();

        em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));

        assertEquals(2, claims.findByClaimantIdOrderBySubmittedAtDesc(lister.getId()).size());
    }

    @Test
    @DisplayName("an agent claim must name the owner")
    void agentMustNameTheOwner() {
        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(claim(AuthorityClaim.ClaimType.AGENT_FOR_OWNER, "")));
    }

    @Test
    @DisplayName("an agent claim that names the owner is accepted")
    void agentWithOwnerNameIsAccepted() {
        AuthorityClaim saved = em.persistAndFlush(claim(AuthorityClaim.ClaimType.AGENT_FOR_OWNER, "Jane Owner"));

        assertEquals("Jane Owner", saved.getOwnerName());
    }

    @Test
    @DisplayName("an identity document belongs to the person and needs no claim")
    void identityDocumentWithoutClaim() {
        VerificationDocument saved = em.persistAndFlush(document(VerificationDocument.DocType.NATIONAL_ID_FRONT, null));

        assertEquals(VerificationDocument.Status.SUBMITTED, saved.getStatus());
        assertEquals(1, documents.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(lister.getId()).size());
    }

    @Test
    @DisplayName("an identity document cannot be attached to a claim")
    void identityDocumentWithClaimIsRefused() {
        AuthorityClaim claim = em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(document(VerificationDocument.DocType.PASSPORT, claim)));
    }

    @Test
    @DisplayName("property evidence needs a claim")
    void evidenceWithoutClaimIsRefused() {
        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(document(VerificationDocument.DocType.TITLE_DEED, null)));
    }

    @Test
    @DisplayName("evidence attached to a claim is listed under that claim")
    void evidenceIsListedUnderItsClaim() {
        AuthorityClaim claim = em.persistAndFlush(claim(AuthorityClaim.ClaimType.OWNER, null));
        em.persistAndFlush(document(VerificationDocument.DocType.TITLE_DEED, claim));

        assertEquals(1, documents.findByClaimIdAndDeletedAtIsNull(claim.getId()).size());
    }

    @Test
    @DisplayName("retention query returns only stored files reviewed before the cutoff")
    void retentionQuery() {
        VerificationDocument old = document(VerificationDocument.DocType.NATIONAL_ID_FRONT, null);
        old.setReviewedAt(LocalDateTime.now().minusDays(91));
        em.persist(old);
        VerificationDocument recent = document(VerificationDocument.DocType.NATIONAL_ID_BACK, null);
        recent.setReviewedAt(LocalDateTime.now().minusDays(10));
        em.persist(recent);
        VerificationDocument unreviewed = document(VerificationDocument.DocType.PASSPORT, null);
        em.persist(unreviewed);
        VerificationDocument alreadyDeleted = document(VerificationDocument.DocType.PASSPORT, null);
        alreadyDeleted.setReviewedAt(LocalDateTime.now().minusDays(200));
        alreadyDeleted.setDeletedAt(LocalDateTime.now().minusDays(100));
        em.persist(alreadyDeleted);
        em.flush();

        List<VerificationDocument> due = documents.findStoredReviewedBefore(LocalDateTime.now().minusDays(90));

        assertEquals(1, due.size());
        assertEquals(old.getId(), due.get(0).getId());
    }

    @Test
    @DisplayName("audit events are listed oldest first and can come from the system (no actor)")
    void auditEventsInOrder() {
        VerificationDocument doc = em.persistAndFlush(document(VerificationDocument.DocType.NATIONAL_ID_FRONT, null));
        auditEvents.saveAndFlush(VerificationAuditEvent.of(lister, VerificationAuditEvent.Action.SUBMITTED, doc, null, null));
        auditEvents.saveAndFlush(VerificationAuditEvent.of(admin, VerificationAuditEvent.Action.VIEWED, doc, null, null));
        auditEvents.saveAndFlush(VerificationAuditEvent.of(null, VerificationAuditEvent.Action.DELETED, doc, null, "retention"));

        List<VerificationAuditEvent> trail = auditEvents.findByDocumentIdOrderByCreatedAtAscIdAsc(doc.getId());

        assertEquals(3, trail.size());
        assertEquals(VerificationAuditEvent.Action.SUBMITTED, trail.get(0).getAction());
        assertEquals(VerificationAuditEvent.Action.DELETED, trail.get(2).getAction());
    }

    @Test
    @DisplayName("an audit event must refer to a document or a claim")
    void auditEventNeedsASubject() {
        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(VerificationAuditEvent.of(admin, VerificationAuditEvent.Action.VIEWED, null, null, null)));
    }

    @Test
    @DisplayName("the database refuses to change or delete an audit event")
    void auditTrailIsAppendOnly() {
        VerificationDocument doc = em.persistAndFlush(document(VerificationDocument.DocType.NATIONAL_ID_FRONT, null));
        auditEvents.saveAndFlush(VerificationAuditEvent.of(admin, VerificationAuditEvent.Action.VIEWED, doc, null, null));

        assertThrows(PersistenceException.class, () -> em.getEntityManager()
                .createNativeQuery("UPDATE verification_audit_event SET note = 'tampered'").executeUpdate());
    }

    @Test
    @DisplayName("new users start NOT_SUBMITTED and new listings NOT_ESTABLISHED")
    void statusColumnsHaveDefaults() {
        em.flush();

        Object identity = em.getEntityManager()
                .createNativeQuery("SELECT identity_status FROM users WHERE id = :id")
                .setParameter("id", lister.getId()).getSingleResult();
        Object authority = em.getEntityManager()
                .createNativeQuery("SELECT authority_status FROM properties WHERE id = :id")
                .setParameter("id", listing.getId()).getSingleResult();

        assertEquals("NOT_SUBMITTED", identity);
        assertEquals("NOT_ESTABLISHED", authority);
    }
}
