package com.hunkyhsu.ragagent.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        String title,
        LocalDateTime createdTime
) { }
