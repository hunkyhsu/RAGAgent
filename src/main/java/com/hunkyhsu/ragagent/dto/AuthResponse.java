package com.hunkyhsu.ragagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hunkyhsu.ragagent.entity.User;
import lombok.Builder;

@Builder
public record AuthResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String accessToken,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String refreshToken,
        // tokenType = "Bearer"
        String tokenType,
        String username,
        User.Role role,      // 新增：告知前端当前角色
        String orgTags,      // 修改：对应 User 实体中的新字段
        @JsonInclude(JsonInclude.Include.NON_NULL)
        long expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long refreshBeforeSeconds
) { }
