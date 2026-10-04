package com.flashsale.catalog.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Date;

/** Access tokens shaped like Auth Service's (HS256, sub = user ID, role claim), signed with the default JWT_SECRET. */
public final class CatalogTestTokens {

    public static final String SECRET = "Zmxhc2gtc2FsZS1lbmdpbmUtc2VjcmV0LWtleS0zMmIh";

    private CatalogTestTokens() {
    }

    public static String token(String userId, String role, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder().setSubject(userId).claim("role", role)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    public static RequestPostProcessor asAdmin() {
        return bearer(token("admin-1", "ADMIN", 300));
    }
}
