package com.roost.controller;

import com.roost.exception.ApiException;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.IdentityStatus;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
import com.roost.service.VerificationDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VerificationControllerTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 9};

    private VerificationDocumentService service;
    private MockMvc mockMvc;
    private User lister;

    @BeforeEach
    void setUp() {
        service = mock(VerificationDocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VerificationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        lister = new User();
        lister.setId(7L);
        lister.setRole(Role.LANDLORD);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lister, null, lister.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static VerificationDocument savedDocument(VerificationDocument.DocType type) {
        VerificationDocument d = new VerificationDocument();
        d.setDocType(type);
        d.setStorageKey("verification/11111111-1111-1111-1111-111111111111.jpg");
        d.setSha256("a".repeat(64));
        d.setAiOpinion("looks fine");
        return d;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "id.jpg", "image/jpeg", JPEG);
    }

    @Test
    @DisplayName("upload returns 201 and passes the type and the file's bytes to the service")
    void uploadPassesTypeAndBytes() throws Exception {
        when(service.submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), any(byte[].class)))
                .thenReturn(savedDocument(VerificationDocument.DocType.NATIONAL_ID_FRONT));

        mockMvc.perform(multipart("/api/verification/identity-documents")
                        .file(file()).param("docType", "NATIONAL_ID_FRONT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docType").value("NATIONAL_ID_FRONT"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(service).submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), bytes.capture());
        assertArrayEquals(JPEG, bytes.getValue());
    }

    @Test
    @DisplayName("the response never exposes the storage key, checksum or AI opinion")
    void responseHasNoInternalFields() throws Exception {
        when(service.submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), any(byte[].class)))
                .thenReturn(savedDocument(VerificationDocument.DocType.PASSPORT));

        mockMvc.perform(multipart("/api/verification/identity-documents")
                        .file(file()).param("docType", "PASSPORT"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.sha256").doesNotExist())
                .andExpect(jsonPath("$.aiOpinion").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    @Test
    @DisplayName("an unknown or missing docType is a 400 and nothing is submitted")
    void badDocTypeIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/verification/identity-documents")
                        .file(file()).param("docType", "DRIVING_LICENCE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/verification/identity-documents").file(file()))
                .andExpect(status().isBadRequest());

        verify(service, never()).submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), any(byte[].class));
    }

    @Test
    @DisplayName("a missing file is a 400 and nothing is submitted")
    void missingFileIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/verification/identity-documents").param("docType", "PASSPORT"))
                .andExpect(status().isBadRequest());

        verify(service, never()).submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), any(byte[].class));
    }

    @Test
    @DisplayName("the service's refusals keep their status: 409 when already verified")
    void serviceRefusalsAreReturned() throws Exception {
        when(service.submitIdentityDocument(any(User.class), any(VerificationDocument.DocType.class), any(byte[].class)))
                .thenThrow(ApiException.conflict("Your identity is already verified."));

        mockMvc.perform(multipart("/api/verification/identity-documents")
                        .file(file()).param("docType", "PASSPORT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Your identity is already verified."));
    }

    @Test
    @DisplayName("the status endpoint returns the status and the documents on file")
    void identityStatusAndDocuments() throws Exception {
        when(service.getIdentityStatus(7L)).thenReturn(IdentityStatus.PENDING);
        when(service.listIdentityDocuments(7L)).thenReturn(List.of(
                savedDocument(VerificationDocument.DocType.NATIONAL_ID_FRONT),
                savedDocument(VerificationDocument.DocType.NATIONAL_ID_BACK)));

        mockMvc.perform(get("/api/verification/identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].docType").value("NATIONAL_ID_FRONT"))
                .andExpect(jsonPath("$.documents[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.documents[0].sha256").doesNotExist());
    }

    @Test
    @DisplayName("a lister with nothing on file sees NOT_SUBMITTED and an empty list")
    void identityWithNothingOnFile() throws Exception {
        when(service.getIdentityStatus(7L)).thenReturn(IdentityStatus.NOT_SUBMITTED);
        when(service.listIdentityDocuments(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/verification/identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.documents.length()").value(0));
    }
}
