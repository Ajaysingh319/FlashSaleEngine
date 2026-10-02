package com.flashsale.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    private final UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static JwtUtils jwtUtils(long accessTtl) {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", accessTtl);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpiration", 604800000L);
        jwtUtils.init();
        return jwtUtils;
    }

    private Authentication authenticate(String token) throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils(60_000), userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/users/me");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        AtomicReference<Authentication> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        return seen.get();
    }

    @Test
    void accessTokenAuthenticatesUserLoadedById() throws Exception {
        when(userDetailsService.loadUserById("user-1")).thenReturn(new User("test@example.com", "pw",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        Authentication authentication = authenticate(jwtUtils(60_000).generateAccessToken("user-1", "CUSTOMER"));

        assertNotNull(authentication);
        assertEquals("test@example.com", authentication.getName());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    @Test
    void refreshTokenDoesNotAuthenticate() throws Exception {
        assertNull(authenticate(jwtUtils(60_000).generateRefreshToken("user-1")));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void expiredTokenDoesNotAuthenticate() throws Exception {
        assertNull(authenticate(jwtUtils(-1_000).generateAccessToken("user-1", "CUSTOMER")));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void malformedTokenDoesNotAuthenticate() throws Exception {
        assertNull(authenticate("not-a-jwt"));
        assertNull(authenticate(null));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void tokenForUnknownUserDoesNotAuthenticate() throws Exception {
        when(userDetailsService.loadUserById("deleted-user")).thenThrow(new UsernameNotFoundException("gone"));

        assertNull(authenticate(jwtUtils(60_000).generateAccessToken("deleted-user", "CUSTOMER")));
    }
}
