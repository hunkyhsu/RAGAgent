package com.hunkyhsu.ragagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hunkyhsu.ragagent.service.UserDetailsService;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final WhiteListConfig whiteListConfig;
	private final RateLimitFilter rateLimitFilter;
	private final SecurityErrorHandler securityErrorHandler;
	private final CorsProperties corsProperties;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			WhiteListConfig whiteListConfig,
			RateLimitFilter rateLimitFilter,
			SecurityErrorHandler securityErrorHandler,
			CorsProperties corsProperties
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.whiteListConfig = whiteListConfig;
		this.rateLimitFilter = rateLimitFilter;
		this.securityErrorHandler = securityErrorHandler;
		this.corsProperties = corsProperties;
	}
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
												   AuthenticationProvider authenticationProvider) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable);
		http.cors(cors -> {});
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(whiteListConfig.getWhiteList().toArray(new String[0])).permitAll()
				.anyRequest().authenticated()

		);
		http.authenticationProvider(authenticationProvider);
		http.exceptionHandling(ex -> ex
				.authenticationEntryPoint(securityErrorHandler)
				.accessDeniedHandler(securityErrorHandler)
		);
		http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
		http.addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class);
		return http.build();
	}

	@Bean
	public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
														 PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
