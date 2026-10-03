package com.flashsale.auth.controller;

import com.flashsale.auth.config.WebSecurityConfig;
import com.flashsale.auth.security.JwtUtils;
import com.flashsale.auth.security.UserDetailsServiceImpl;
import com.flashsale.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bean Validation on the public auth endpoints: invalid bodies are rejected before reaching AuthService. */
@WebMvcTest(controllers = AuthController.class)
@Import(WebSecurityConfig.class)
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 400 INVALID_REQUEST envelope whose message names the problem without leaking internals; service never called. */
    private void assertRejected(ResultActions result, String expectedMessagePart) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message", allOf(containsString(expectedMessagePart),
                        not(containsString("com.flashsale")), not(containsString("Exception")), not(containsString("jackson")))))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(authService);
    }

    // --- Register ---

    @Test
    void registerWithBlankEmailIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/register", "{\"email\":\"\",\"password\":\"password123\"}"), "email");
    }

    @Test
    void registerWithMissingEmailIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/register", "{\"password\":\"password123\"}"), "email");
    }

    @Test
    void registerWithInvalidEmailFormatIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/register", "{\"email\":\"not-an-email\",\"password\":\"password123\"}"), "email");
    }

    @Test
    void registerWithBlankPasswordIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/register", "{\"email\":\"user@example.com\",\"password\":\"\"}"), "password");
    }

    @Test
    void registerWithShortPasswordIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/register", "{\"email\":\"user@example.com\",\"password\":\"short\"}"),
                "Password must be at least 8 characters long");
    }

    // --- Login ---

    @Test
    void loginWithBlankEmailIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/login", "{\"email\":\" \",\"password\":\"password123\"}"), "email");
    }

    @Test
    void loginWithBlankPasswordIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"\"}"), "password");
    }

    @Test
    void loginWithMissingPasswordIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/login", "{\"email\":\"user@example.com\"}"), "password");
    }

    // --- Refresh ---

    @Test
    void refreshWithBlankTokenIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"\"}"), "refreshToken");
    }

    @Test
    void refreshWithMissingTokenIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/refresh", "{}"), "refreshToken");
    }

    // --- Malformed bodies ---

    @Test
    void malformedJsonIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":"), "not valid JSON");
    }

    @Test
    void missingBodyIs400() throws Exception {
        assertRejected(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)), "missing");
    }

    @Test
    void wronglyTypedFieldIs400() throws Exception {
        assertRejected(postJson("/api/v1/auth/login", "{\"email\":{\"nested\":true},\"password\":\"password123\"}"),
                "Invalid value for field 'email'");
    }

    // --- Valid requests are unchanged ---

    @Test
    void validRegisterReachesService() throws Exception {
        postJson("/api/v1/auth/register", "{\"email\":\"user@example.com\",\"password\":\"password123\"}")
                .andExpect(status().isOk());
        verify(authService).register(any());
    }

    @Test
    void validLoginReachesService() throws Exception {
        postJson("/api/v1/auth/login", "{\"email\":\"user@example.com\",\"password\":\"password123\"}")
                .andExpect(status().isOk());
        verify(authService).login(any());
    }

    @Test
    void validRefreshReachesService() throws Exception {
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"some.refresh.token\"}")
                .andExpect(status().isOk());
        verify(authService).refreshToken(any());
    }
}
