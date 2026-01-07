package com.hunkyhsu.ragagent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        String username,
        @NotBlank
        @Size(min = 8, max = 128)
        String password,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @Size(max = 255)
        String orgTags
) { }
