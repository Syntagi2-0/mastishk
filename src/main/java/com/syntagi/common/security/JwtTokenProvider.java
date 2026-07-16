package com.syntagi.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserDetails userDetails) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())));

        if (userDetails instanceof SyntagiPrincipal principal) {
            builder.claim("userId", principal.userId().toString())
                    .claim("businessId", principal.businessId().toString())
                    .claim("role", principal.role().name());
        }

        return builder.signWith(signingKey).compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        if (!claims.getSubject().equals(userDetails.getUsername())
                || !claims.getExpiration().after(new Date())
                || !userDetails.isEnabled()
                || !userDetails.isAccountNonLocked()) {
            return false;
        }

        if (userDetails instanceof SyntagiPrincipal principal) {
            return principal.userId().toString().equals(claims.get("userId", String.class))
                    && principal.businessId().toString().equals(claims.get("businessId", String.class))
                    && principal.role().name().equals(claims.get("role", String.class));
        }
        return false;
    }

    public long getAccessTokenExpiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
