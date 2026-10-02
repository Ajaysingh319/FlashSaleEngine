package com.flashsale.auth.service;

import com.flashsale.auth.document.User;
import com.flashsale.auth.dto.*;
import com.flashsale.auth.repository.UserRepository;
import com.flashsale.auth.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId("user-1");
            return saved;
        });
        when(jwtUtils.generateAccessToken(anyString(), anyString())).thenReturn("accessToken");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refreshToken");
        when(jwtUtils.getJwtExpiration()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        assertEquals(86400000L, response.getExpiresIn());
        verify(userRepository).save(any(User.class));
        verify(jwtUtils).generateAccessToken("user-1", "CUSTOMER");
        verify(jwtUtils).generateRefreshToken("user-1");
    }

    @Test
    void testRegisterEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User existingUser = new User();
        existingUser.setEmail(request.getEmail());
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existingUser));

        assertThrows(RuntimeException.class, () -> authService.register(request));
    }

    @Test
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        User user = new User();
        user.setId("user-1");
        user.setEmail("test@example.com");
        user.setRole("CUSTOMER");
        user.setPassword("encodedPassword");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(anyString(), anyString())).thenReturn("accessToken");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("refreshToken");
        when(jwtUtils.getJwtExpiration()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        assertEquals(86400000L, response.getExpiresIn());
        verify(jwtUtils).generateAccessToken("user-1", "CUSTOMER");
        verify(jwtUtils).generateRefreshToken("user-1");
    }

    @Test
    void testRefreshTokenSuccess() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("validRefreshToken");

        when(jwtUtils.isTokenExpired(request.getRefreshToken())).thenReturn(false);
        when(jwtUtils.extractUserId(request.getRefreshToken())).thenReturn("user-1");

        User user = new User();
        user.setId("user-1");
        user.setEmail("test@example.com");
        user.setRole("CUSTOMER");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtUtils.generateRefreshToken(anyString())).thenReturn("newRefreshToken");
        when(jwtUtils.getJwtExpiration()).thenReturn(86400000L);

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
        assertEquals("newRefreshToken", response.getRefreshToken());
        assertEquals(86400000L, response.getExpiresIn());
        verify(jwtUtils).generateAccessToken("user-1", "CUSTOMER");
        verify(jwtUtils).generateRefreshToken("user-1");
    }

    @Test
    void testRefreshTokenExpired() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("expiredRefreshToken");

        when(jwtUtils.isTokenExpired(request.getRefreshToken())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.refreshToken(request));
    }

    @Test
    void testGetCurrentUser() {
        String email = "test@example.com";
        User user = new User();
        user.setId("userId");
        user.setEmail(email);
        user.setRole("CUSTOMER");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserResponse response = authService.getCurrentUser(email);

        assertNotNull(response);
        assertEquals("userId", response.getId());
        assertEquals(email, response.getEmail());
        assertEquals("CUSTOMER", response.getRole());
    }

    @Test
    void testLogout() {
        // Should not throw any exception
        assertDoesNotThrow(() -> authService.logout());
    }
}