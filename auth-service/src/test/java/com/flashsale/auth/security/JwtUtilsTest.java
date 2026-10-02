package com.flashsale.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    /** Same default value as JWT_SECRET in application.yml of every service. */
    private static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "another-flash-sale-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));

    private static JwtUtils jwtUtils(String secret, long accessTtl) {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secretKey", secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", accessTtl);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpiration", 604800000L);
        jwtUtils.init();
        return jwtUtils;
    }

    @Test
    void accessTokenUsesUserIdSubjectRoleClaimAndHs256() {
        String token = jwtUtils(SECRET, 60_000).generateAccessToken("665f1c2ab3e4d5f6a7b8c9d0", "CUSTOMER");

        var jws = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).build()
                .parseClaimsJws(token);
        assertEquals("HS256", jws.getHeader().getAlgorithm());
        assertEquals("665f1c2ab3e4d5f6a7b8c9d0", jws.getBody().getSubject());
        assertEquals("CUSTOMER", jws.getBody().get("role", String.class));
        assertNotNull(jws.getBody().getExpiration());
    }

    @Test
    void signatureMatchesStandardBase64DecodedSecretWithHmacSha256() throws Exception {
        // The gateway decodes JWT_SECRET with java.util.Base64 and verifies HmacSHA256; prove the bytes agree.
        String token = jwtUtils(SECRET, 60_000).generateAccessToken("user-1", "CUSTOMER");
        String[] parts = token.split("\\.");

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertArrayEquals(expected, Base64.getUrlDecoder().decode(parts[2]));
    }

    @Test
    void parsesOwnSignedTokens() {
        JwtUtils jwtUtils = jwtUtils(SECRET, 60_000);
        String accessToken = jwtUtils.generateAccessToken("user-1", "ADMIN");
        String refreshToken = jwtUtils.generateRefreshToken("user-1");

        assertEquals("user-1", jwtUtils.extractUserId(accessToken));
        assertEquals("ADMIN", jwtUtils.parseClaims(accessToken).get(JwtUtils.ROLE_CLAIM, String.class));
        assertFalse(jwtUtils.isTokenExpired(accessToken));

        Claims refreshClaims = jwtUtils.parseClaims(refreshToken);
        assertEquals("user-1", refreshClaims.getSubject());
        assertNull(refreshClaims.get(JwtUtils.ROLE_CLAIM));
    }

    @Test
    void expiredTokenIsReportedExpiredAndRejected() {
        JwtUtils jwtUtils = jwtUtils(SECRET, -1_000);
        String expired = jwtUtils.generateAccessToken("user-1", "CUSTOMER");

        assertTrue(jwtUtils.isTokenExpired(expired));
        assertThrows(JwtException.class, () -> jwtUtils.parseClaims(expired));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String foreign = jwtUtils(OTHER_SECRET, 60_000).generateAccessToken("user-1", "ADMIN");

        JwtUtils jwtUtils = jwtUtils(SECRET, 60_000);
        assertThrows(JwtException.class, () -> jwtUtils.parseClaims(foreign));
        assertThrows(JwtException.class, () -> jwtUtils.extractUserId(foreign));
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().setSubject("user-1").claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000)).compact();

        assertThrows(JwtException.class, () -> jwtUtils(SECRET, 60_000).parseClaims(unsigned));
    }

    @Test
    void nonHs256TokenIsRejected() {
        String longSecret = Base64.getEncoder().encodeToString(new byte[64]);
        String hs512 = Jwts.builder().setSubject("user-1").claim("role", "CUSTOMER")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(longSecret)), SignatureAlgorithm.HS512)
                .compact();

        assertThrows(JwtException.class, () -> jwtUtils(longSecret, 60_000).parseClaims(hs512));
    }
}
