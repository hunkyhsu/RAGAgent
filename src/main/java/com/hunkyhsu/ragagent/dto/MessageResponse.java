package com.hunkyhsu.ragagent.dto;

import com.hunkyhsu.ragagent.entity.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Message.Role role,
        String content,
        LocalDateTime createdTime
) { }
