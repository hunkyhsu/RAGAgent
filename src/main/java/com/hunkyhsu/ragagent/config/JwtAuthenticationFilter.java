package com.hunkyhsu.ragagent.config;

import java.io.IOException;

import com.hunkyhsu.ragagent.service.UserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.userdetails.UserDetails;

import com.hunkyhsu.ragagent.service.JwtService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final WhiteListConfig whiteListConfig;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public JwtAuthenticationFilter(JwtService jwtService,
								   UserDetailsService userDetailsService,
								   WhiteListConfig whiteListConfig) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.whiteListConfig = whiteListConfig;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
		// 白名单直接放行，不走下面的 JWT 校验逻辑
		return whiteListConfig.getWhiteList().stream()
				.anyMatch(pattern -> pathMatcher.match(pattern, request.getServletPath()));
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	) throws ServletException, IOException {

		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = authHeader.substring(BEARER_PREFIX.length()).trim();

		try {
			String username = jwtService.extractUsername(token);
			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				UserDetails userDetails = userDetailsService.loadUserByUsername(username);
				// 校验逻辑包含：Token 有效性或宽限期校验
				if (jwtService.isValidToken(token, userDetails)) {
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userDetails, null, userDetails.getAuthorities());
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			}
		} catch (Exception ex) {
			SecurityContextHolder.clearContext();
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"message\":\"invalid_token\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}
}
