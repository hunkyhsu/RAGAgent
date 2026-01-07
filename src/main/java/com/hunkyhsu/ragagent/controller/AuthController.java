package com.hunkyhsu.ragagent.controller;

import com.hunkyhsu.ragagent.dto.AuthResponse;
import com.hunkyhsu.ragagent.dto.LoginRequest;
import com.hunkyhsu.ragagent.dto.RefreshTokenRequest;
import com.hunkyhsu.ragagent.dto.RegisterRequest;
import com.hunkyhsu.ragagent.entity.User;
import com.hunkyhsu.ragagent.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

		return ResponseEntity.ok(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
		return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
	}

	@GetMapping("/me")
	public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(
				AuthResponse.builder()
						.username(user.getUsername())
						.tokenType("Bearer")
						.role(user.getRole())
						.orgTags(user.getOrgTags())
						.build()
		);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
		if (user != null) {
			authService.logout(user.getUsername());
		}
		return ResponseEntity.ok().build();
	}
}
