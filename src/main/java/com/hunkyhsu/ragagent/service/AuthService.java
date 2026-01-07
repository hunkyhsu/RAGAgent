package com.hunkyhsu.ragagent.service;

import com.hunkyhsu.ragagent.dto.*;
import com.hunkyhsu.ragagent.entity.RefreshToken;
import com.hunkyhsu.ragagent.entity.User;
import com.hunkyhsu.ragagent.exception.InvalidTokenException;
import com.hunkyhsu.ragagent.repository.RefreshTokenRepository;
import com.hunkyhsu.ragagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userRepository.findByUsername(request.username()).isPresent()){
			throw new ResponseStatusException(HttpStatus.CONFLICT, "user_exists");
		}
		if (userRepository.findByEmail(request.email()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email_exists");
		}
		User user = User.builder()
				.username(request.username())
				.password(passwordEncoder.encode(request.password()))
				.email(request.email())
				.role(User.Role.USER)
				.orgTags(request.orgTags())
				.build();
		userRepository.save(user);
		return generateAuthResponse(user);
	}

	public AuthResponse login(LoginRequest request) {
		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.username(), request.password())
		);
		User user = userRepository.findByUsername(request.username())
				.orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
		// 3. 签发令牌
		return generateAuthResponse(user);
	}

	// TODO: ADD REGISTER ADMIN

	@Transactional
	public AuthResponse refreshToken(String refreshToken) {
		// 1. 提取用户名（JwtService 内部需处理过期提取逻辑）
		String username = jwtService.extractUsername(refreshToken);
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new InvalidTokenException("无效的刷新令牌"));
		// 2. 验证 Refresh Token 物理合法性
		if (!jwtService.isRefreshTokenValid(refreshToken, username)) {
			throw new InvalidTokenException("刷新令牌已失效，请重新登录");
		}
		// 3. 校验 Refresh Token 是否被撤销或不存在
		RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(hashToken(refreshToken))
				.orElseThrow(() -> new InvalidTokenException("刷新令牌已失效，请重新登录"));
		if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			revokeToken(storedToken);
			throw new InvalidTokenException("刷新令牌已失效，请重新登录");
		}
		// 4. 轮换 Refresh Token：撤销旧的，签发新的
		revokeToken(storedToken);
		return generateAuthResponse(user);
	}

	private AuthResponse generateAuthResponse(User user) {
		String accessToken = jwtService.generateToken(user);
		String refreshToken = jwtService.generateRefreshToken(user);

		// 从 JwtService 获取配置的有效期秒数
		long expiresIn = jwtService.getAccessTokenExpirationTime();

		saveRefreshToken(user, refreshToken);

		return AuthResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.tokenType("Bearer")
				.username(user.getUsername())
				.orgTags(user.getOrgTags())
				.role(user.getRole())
				.expiresInSeconds(expiresIn)
				.refreshBeforeSeconds(jwtService.getProactiveRefreshTime() / 1000)
				.build();
	}

	@Transactional
	public void logout(String username) {
		// 目前没有 Redis，仅做审计记录
		log.info("User {} logged out successfully at {}", username, LocalDateTime.now());
		userRepository.findByUsername(username).ifPresent(user -> {
			refreshTokenRepository.findByUserAndRevokedFalse(user)
					.forEach(this::revokeToken);
		});
	}

	private void saveRefreshToken(User user, String refreshToken) {
		LocalDateTime expiresAt = LocalDateTime.now()
				.plus(Duration.ofMillis(jwtService.getRefreshTokenExpirationTime()));
		RefreshToken token = RefreshToken.builder()
				.user(user)
				.tokenHash(hashToken(refreshToken))
				.expiresAt(expiresAt)
				.revoked(false)
				.build();
		refreshTokenRepository.save(token);
	}

	private void revokeToken(RefreshToken token) {
		token.setRevoked(true);
		token.setRevokedAt(LocalDateTime.now());
		refreshTokenRepository.save(token);
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				String h = Integer.toHexString(b & 0xff);
				if (h.length() == 1) {
					hex.append('0');
				}
				hex.append(h);
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
