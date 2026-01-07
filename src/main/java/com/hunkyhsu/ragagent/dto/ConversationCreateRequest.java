package com.hunkyhsu.ragagent.dto;

import jakarta.validation.constraints.Size;

public record ConversationCreateRequest(
        @Size(max = 255)
        String title
) { }
