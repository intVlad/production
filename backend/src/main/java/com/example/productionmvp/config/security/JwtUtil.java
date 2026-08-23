package com.example.productionmvp.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // The literal default from application.yml - if this is what's active, JWT_SECRET was
    // never set. Anyone who has cloned this repo (it's a public starter project) can sign
    // their own admin token with it, so this must never be true for a real deployment.
    private static final String INSECURE_DEFAULT_SECRET =
            "c2VjdXJlLXByb2R1Y3Rpb24tbXZwLWp3dC1zZWNyZXQta2V5LTI1Ni1iaXRzLW1pbmltdW0tcmVxdWlyZWQ=";

    private final SecretKey secretKey;
    private final long jwtExpirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        if (INSECURE_DEFAULT_SECRET.equals(secret)) {
            // A logged warning didn't stop this from being live-exploitable: the app still
            // started and served traffic on the public secret, so anyone who forged a token
            // with it (trivial - it's sitting in this file, and application.yml, in plain
            // sight) got in. Generating a fresh random key here instead means that attack no
            // longer works even if JWT_SECRET is never configured - with zero deployment
            // friction, since DataSeeder already regenerates every worker's UUID on every
            // restart, so any previously-issued token is already dead after a restart anyway.
            logger.warn("=================================================================");
            logger.warn("SECURITY WARNING: jwt.secret is the public default from application.yml.");
            logger.warn("Generating a random in-memory secret for this run instead of using it.");
            logger.warn("Tokens will stop working on the next restart. Set the JWT_SECRET");
            logger.warn("environment variable to a unique, persistent value for a real deployment.");
            logger.warn("=================================================================");
            this.secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS384);
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.jwtExpirationMs = expirationMs;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
