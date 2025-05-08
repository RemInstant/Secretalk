package org.reminstant.cryptomessengerserver.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.DigestAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
  private final String jwtSigningKey;
  private final JwtParser jwtParser;

  public JwtService(@Value("${token.signing.key}") String jwtSigningKey) {
    this.jwtSigningKey = jwtSigningKey;
    jwtParser = Jwts.parser().verifyWith(getSigningKey()).build();
  }

  public String generateToken(UserDetails userDetails, Duration ttl) {
    return generateToken(Collections.emptyMap(), userDetails, ttl);
  }

  public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(getSigningKey())
        .compact();
  }

  public boolean isTokenValid(String token) {
    try {
      jwtParser.parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public String extractUsername(String token) {
    return jwtParser.parseSignedClaims(token).getPayload().getSubject();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtSigningKey);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
