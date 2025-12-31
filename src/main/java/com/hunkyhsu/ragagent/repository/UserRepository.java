package com.hunkyhsu.ragagent.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.hunkyhsu.ragagent.dto.UserAuth;

@Repository
public class UserRepository {
	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<UserAuth> findByEmail(String email) {
		return jdbcTemplate.query(
				"SELECT id, email, password_bcrypt FROM users WHERE email = ?",
				(rs, rowNum) -> new UserAuth(
						rs.getLong("id"),
						rs.getString("email"),
						rs.getString("password_bcrypt")
				),
				email
		).stream().findFirst();
	}

	public long create(String email, String passwordBcrypt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO users (email, password_bcrypt) VALUES (?, ?)",
					Statement.RETURN_GENERATED_KEYS
			);
			ps.setString(1, email);
			ps.setString(2, passwordBcrypt);
			return ps;
		}, keyHolder);
		if (keyHolder.getKey() == null) {
			throw new IllegalStateException("Failed to create user");
		}
		return keyHolder.getKey().longValue();
	}
}
