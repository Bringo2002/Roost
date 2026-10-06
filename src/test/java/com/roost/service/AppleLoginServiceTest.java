package com.roost.service;

import com.roost.dto.AppleAuthRequest;
import com.roost.dto.AuthResponse;
import com.roost.exception.ApiException;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.UserRepository;
import com.roost.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleLoginServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AppleAuthService appleAuthService;

    private AppleLoginService service;

    @BeforeEach
    void setUp() {
        service = new AppleLoginService(userRepository, passwordEncoder, jwtService, appleAuthService);
    }

    private static AppleAuthRequest request(String name) {
        AppleAuthRequest r = new AppleAuthRequest();
        r.setIdToken("firebase-token");
        r.setName(name);
        return r;
    }

    @Test
    @DisplayName("unverifiable token -> 400, nothing read or written")
    void unverifiedTokenIsRejected() {
        when(appleAuthService.verifyAppleToken("firebase-token")).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.loginWithApple(request(null)));

        assertEquals(400, ex.getStatus().value());
        verifyNoInteractions(userRepository, jwtService, passwordEncoder);
    }

    @Test
    @DisplayName("existing email -> logs into that account, isNewUser=false, nothing saved")
    void existingUserLogsIn() {
        User existing = new User();
        existing.setEmail("a@b.com");
        when(appleAuthService.verifyAppleToken("firebase-token"))
                .thenReturn(new AppleAuthService.AppleIdentity("a@b.com", "Ada"));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("jwt-existing");

        AuthResponse res = service.loginWithApple(request(null));

        assertEquals("jwt-existing", res.getToken());
        assertFalse(res.isNewUser());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("new email -> TENANT account with encoded random password, isNewUser=true")
    void newUserIsCreatedAsTenant() {
        when(appleAuthService.verifyAppleToken("firebase-token"))
                .thenReturn(new AppleAuthService.AppleIdentity("new@b.com", "Ada"));
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-new");

        AuthResponse res = service.loginWithApple(request(null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("new@b.com", saved.getValue().getEmail());
        assertEquals("Ada", saved.getValue().getName());
        assertEquals(Role.TENANT, saved.getValue().getRole());
        assertEquals("hashed", saved.getValue().getPassword());
        assertEquals("jwt-new", res.getToken());
        assertTrue(res.isNewUser());
    }

    @Test
    @DisplayName("name falls back to client-forwarded name, then to a neutral default")
    void nameFallbackOrder() {
        when(appleAuthService.verifyAppleToken("firebase-token"))
                .thenReturn(new AppleAuthService.AppleIdentity("new@b.com", null));
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt");

        service.loginWithApple(request("  Grace Hopper "));
        service.loginWithApple(request("   "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals("Grace Hopper", saved.getAllValues().get(0).getName());
        assertNotNull(saved.getAllValues().get(1).getName());
        assertEquals("Roost User", saved.getAllValues().get(1).getName());
    }
}
