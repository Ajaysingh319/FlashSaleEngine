package com.flashsale.catalog.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

/** Generates a short-lived service JWT accepted by Reservation Service's /internal endpoints. */
@Component
public class InternalJwtTokenProvider {
    @Value("${app.jwt.secret}") private String secret;
    private Key signingKey;

    @PostConstruct
    void initialize() { signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); }

    public String token() {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("catalog-service").claim("role", "INTERNAL")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }
}
