package com.flashsale.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * Test helper that signs tokens with the same configuration as Auth Service's JwtUtils
 * (jjwt, Base64-decoded JWT_SECRET, HS256, sub = user ID, "role" claim). Kept as a copy
 * rather than a dependency so the services stay independent.
 */
public final class AuthServiceTokens {

    /** Same default value as JWT_SECRET in every service's application.yml. */
    public static final String DEFAULT_SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    private AuthServiceTokens() {
    }

    /** Mirrors JwtUtils.generateAccessToken. */
    public static String accessToken(String base64Secret, String userId, String role, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(Map.of("role", role))
                .setSubject(userId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(key(base64Secret), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Mirrors JwtUtils.generateRefreshToken (no role claim). */
    public static String refreshToken(String base64Secret, String userId, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(key(base64Secret), SignatureAlgorithm.HS256)
                .compact();
    }

    public static Key key(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }
}
