package com.hunkyhsu.ragagent.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
