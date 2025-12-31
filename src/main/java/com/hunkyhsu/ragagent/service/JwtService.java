package com.hunkyhsu.ragagent.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private final Key key;
	private final long ttlSeconds;

	public JwtService(
			@Value("${security.jwt.secret}") String secret,
			@Value("${security.jwt.ttl-seconds}") long ttlSeconds
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.ttlSeconds = ttlSeconds;
	}

	public String issueToken(long userId, String email) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(ttlSeconds);
		return Jwts.builder()
				.setSubject(Long.toString(userId))
				.claim("email", email)
				.setIssuedAt(Date.from(now))
				.setExpiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
	}

	public JwtUser parseToken(String token) {
		Jws<Claims> parsed = Jwts.parserBuilder()
				.setSigningKey(key)
				.build()
				.parseClaimsJws(token);
		Claims claims = parsed.getBody();
		long userId = Long.parseLong(claims.getSubject());
		String email = claims.get("email", String.class);
		return new JwtUser(userId, email);
	}

	public long getTtlSeconds() {
		return ttlSeconds;
	}

	public record JwtUser(long userId, String email) {
	}
}
