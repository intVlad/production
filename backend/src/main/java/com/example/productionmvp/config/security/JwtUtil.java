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
                   @Value("${jwt.expiration-ms}") long expirationMs,
                   com.example.productionmvp.config.DeploymentMode deploymentMode) {
        if (INSECURE_DEFAULT_SECRET.equals(secret)) {
            // A logged warning didn't stop this from being live-exploitable: the app still
            // started and served traffic on the public secret, so anyone who forged a token
            // with it (trivial - it's sitting in this file, and application.yml, in plain
            // sight) got in.
            if (deploymentMode.isPersistentDeployment()) {
                // Generating a key here was safe only while the database was in-memory: every
                // account was recreated on restart anyway, so no session could outlive one.
                // Against a real database the accounts persist and the key does not, which
                // would sign every operator out on each restart and leave the deployment
                // depending on a secret that exists nowhere. Refuse to start instead - this is
                // one environment variable, and the alternative is a subtle, recurring outage.
                throw new IllegalStateException(
                        "JWT_SECRET is not set and this deployment uses a persistent database ("
                        + deploymentMode.getDatasourceUrl() + "). Set JWT_SECRET to a unique, "
                        + "persistent, base64-encoded value of at least 48 bytes. Generate one with: "
                        + "openssl rand -base64 48");
            }
            logger.warn("=================================================================");
            logger.warn("jwt.secret is the public default from application.yml.");
            logger.warn("Generating a random in-memory secret for this run instead of using it.");
            logger.warn("Tokens stop working on restart - fine here, because this run uses an");
            logger.warn("embedded database that is wiped on restart too. A persistent database");
            logger.warn("refuses to start without JWT_SECRET.");
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
