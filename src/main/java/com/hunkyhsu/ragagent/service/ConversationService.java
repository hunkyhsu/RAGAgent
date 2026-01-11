package com.hunkyhsu.ragagent.service;

import com.hunkyhsu.ragagent.dto.ConversationResponse;
import com.hunkyhsu.ragagent.dto.MessageResponse;
import com.hunkyhsu.ragagent.entity.Conversation;
import com.hunkyhsu.ragagent.entity.Message;
import com.hunkyhsu.ragagent.entity.User;
import com.hunkyhsu.ragagent.repository.ConversationRepository;
import com.hunkyhsu.ragagent.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final String DEFAULT_TITLE = "New Chat";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public ConversationResponse createConversation(User user, String title) {
        String finalTitle = (title == null || title.trim().isEmpty()) ? DEFAULT_TITLE : title.trim();
        Conversation conversation = Conversation.builder()
                .user(user)
                .title(finalTitle)
                .build();
        Conversation saved = conversationRepository.save(conversation);
        return toConversationResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(User user) {
        return conversationRepository.findByUserOrderByCreatedTimeDesc(user)
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationHistory(User user, Long conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation_not_found"));
        return messageRepository.findByConversationOrderByCreatedTimeAsc(conversation)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public ConversationResponse renameConversation(User user, Long conversationId, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title_required");
        }
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation_not_found"));
        conversation.setTitle(title.trim());
        Conversation saved = conversationRepository.save(conversation);
        return toConversationResponse(saved);
    }

    @Transactional
    public void deleteConversation(User user, Long conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation_not_found"));
        conversationRepository.delete(conversation);
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedTime()
        );
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedTime()
        );
    }
}
