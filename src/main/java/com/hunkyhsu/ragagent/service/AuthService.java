package com.hunkyhsu.ragagent.service;

import com.hunkyhsu.ragagent.dto.AuthResponse;
import com.hunkyhsu.ragagent.dto.LoginRequest;
import com.hunkyhsu.ragagent.dto.RegisterRequest;
import com.hunkyhsu.ragagent.dto.UserAuth;
import com.hunkyhsu.ragagent.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		String password = requirePassword(request.password());
		if (userRepository.findByEmail(email).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email_exists");
		}
		String hashed = passwordEncoder.encode(password);
		long userId = userRepository.create(email, hashed);
		return issueToken(userId, email);
	}

	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		String password = requirePassword(request.password());
		UserAuth user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials"));
		if (!passwordEncoder.matches(password, user.passwordBcrypt())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials");
		}
		return issueToken(user.id(), user.email());
	}

	private AuthResponse issueToken(long userId, String email) {
		String token = jwtService.issueToken(userId, email);
		return new AuthResponse(token, "Bearer", jwtService.getTtlSeconds());
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");
		}
		return email.trim().toLowerCase();
	}

	private String requirePassword(String password) {
		if (password == null || password.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password_required");
		}
		return password;
	}
}
