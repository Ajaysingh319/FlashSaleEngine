package com.flashsale.auth.controller;

import com.flashsale.auth.config.WebSecurityConfig;
import com.flashsale.auth.dto.UpdateProfileRequest;
import com.flashsale.auth.dto.UserResponse;
import com.flashsale.auth.exception.UserNotFoundException;
import com.flashsale.auth.security.JwtUtils;
import com.flashsale.auth.security.UserDetailsServiceImpl;
import com.flashsale.auth.service.UserProfileService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET/PUT /api/v1/users/me: the profile is always the token owner's, and only valid edits reach the service. */
@WebMvcTest(controllers = UserController.class)
@Import(WebSecurityConfig.class)
class UserControllerTest {
    private static final String EMAIL = "fan@example.com";
    private static final String BEARER = "Bearer access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void authenticatedCustomer() {
        Claims claims = Jwts.claims().setSubject("user-1");
        claims.put(JwtUtils.ROLE_CLAIM, "CUSTOMER");
        when(jwtUtils.parseClaims("access-token")).thenReturn(claims);
        when(userDetailsService.loadUserById("user-1"))
                .thenReturn(new User(EMAIL, "hash", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    @Test
    void returnsTheTokenOwnersProfile() throws Exception {
        when(userProfileService.getProfile(EMAIL))
                .thenReturn(new UserResponse("user-1", EMAIL, "Asha Rao", "CUSTOMER", Instant.EPOCH));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updatesTheTokenOwnersFullName() throws Exception {
        when(userProfileService.updateProfile(EMAIL, new UpdateProfileRequest("Asha Rao")))
                .thenReturn(new UserResponse("user-1", EMAIL, "Asha Rao", "CUSTOMER", Instant.EPOCH));

        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Asha Rao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Rao"));
    }

    @Test
    void blankOrOverlongNameIsRejected() throws Exception {
        for (String body : List.of("{\"fullName\":\"  \"}", "{}", "{\"fullName\":\"" + "x".repeat(101) + "\"}")) {
            mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, BEARER)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verify(userProfileService, never()).updateProfile(any(), any());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isForbidden());
        verifyNoInteractions(userProfileService);
    }

    @Test
    void deletedAccountIsNotFound() throws Exception {
        when(userProfileService.getProfile(EMAIL)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }
}
