package com.hunkyhsu.ragagent.controller;

import com.hunkyhsu.ragagent.dto.ConversationCreateRequest;
import com.hunkyhsu.ragagent.dto.ConversationResponse;
import com.hunkyhsu.ragagent.dto.MessageResponse;
import com.hunkyhsu.ragagent.entity.User;
import com.hunkyhsu.ragagent.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/create")
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ConversationCreateRequest request
    ) {
        return ResponseEntity.ok(conversationService.createConversation(user, request.title()));
    }

    @GetMapping("/list")
    public ResponseEntity<List<ConversationResponse>> listConversations(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(conversationService.listConversations(user));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getConversationMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(conversationService.getConversationHistory(user, conversationId));
    }
}
