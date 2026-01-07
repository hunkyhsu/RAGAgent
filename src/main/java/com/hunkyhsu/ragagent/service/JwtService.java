package com.hunkyhsu.ragagent.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.hunkyhsu.ragagent.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

// JWT = Header + Payload(Claims) + Signature
@Service
@Getter
public class JwtService {

	@Value("${security.jwt.secret-key}")
	private String secretKeyBase64;
	@Value("${security.jwt.access-token-expiration-time}")
	private long accessTokenExpirationTime;
	@Value("${security.jwt.refresh-token-expiration-time}")
	private long refreshTokenExpirationTime;
	@Value("${security.jwt.proactive-refresh-time}")
	private long proactiveRefreshTime;
	@Value("${security.jwt.grace-period-refresh-time}")
	private long gracePeriodRefreshTime;
	@Value("${security.jwt.issuer}")
	private String issuer;
	@Value("${security.jwt.audience}")
	private String audience;
	@Value("${security.jwt.clock-skew-seconds}")
	private long clockSkewSeconds;

	public String generateToken(UserDetails userDetails) {
		Map<String, Object> extraClaims = new HashMap<>();
		String tokenId = generateTokenId();

		if (userDetails instanceof User user){
			extraClaims.put("orgTags", user.getOrgTags());
			extraClaims.put("role",  user.getRole());
		}

		return Jwts.builder()
				.setClaims(extraClaims)
				.setSubject(userDetails.getUsername())
				.setIssuer(issuer)
				.setAudience(audience)
				.setId(tokenId)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + accessTokenExpirationTime))
				.signWith(getSignInKey(), SignatureAlgorithm.HS256)
				.compact();
	}

	public String generateRefreshToken(User user) {
		String tokenId = generateTokenId();
		return Jwts.builder()
				.setSubject(user.getUsername())
				.setIssuer(issuer)
				.setAudience(audience)
				.setId(tokenId)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpirationTime))
				.signWith(getSignInKey(), SignatureAlgorithm.HS256)
				.compact();
	}

	// 验证 Refresh Token 是否在数据库/Redis 中存在且有效（此处简化为逻辑验证）
	public boolean isRefreshTokenValid(String refreshToken, String username) {
		// 实际生产中应查 Redis 确认该 Refresh Token 未被禁用
		return extractUsername(refreshToken).equals(username) && !isTokenExpired(refreshToken);
	}

	public boolean isTokenExpired(String token) {
		try {
			// 提取 Token 中的过期时间字段（exp）
			Date expiration = extractClaim(token, Claims::getExpiration);
			// 如果过期时间在当前时间之前，则返回 true
			return expiration.before(new Date());
		} catch (ExpiredJwtException e) {
			// 如果在解析时 io.jsonwebtoken 就抛出了 ExpiredJwtException
			// 说明该 Token 已经过期了，直接返回 true
			return true;
		} catch (Exception e) {
			// 其他解析异常（签名错误、格式错误等）也视为无效/过期
			return true;
		}
	}

	public String extractUsername(String token) {
		try{
			return extractClaim(token, Claims::getSubject);
		} catch (ExpiredJwtException e){
			return e.getClaims().getSubject();
		}
	}

	// 校验逻辑包含：Token 有效性或宽限期校验
	public Boolean isValidToken(String token, UserDetails userDetails) {
		final String username = extractUsername(token);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
	}

	private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(getSignInKey())
				.setAllowedClockSkewSeconds(clockSkewSeconds)
				.requireIssuer(issuer)
				.requireAudience(audience)
				.build()
				.parseClaimsJws(token)
				.getBody();
	}

	private Key getSignInKey() {
		byte[] keyBytes = Decoders.BASE64.decode(secretKeyBase64);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes) for HS256");
		}
		return Keys.hmacShaKeyFor(keyBytes);
	}
	// used for Redis
	private String generateTokenId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

}
