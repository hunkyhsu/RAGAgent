package com.hunkyhsu.ragagent.repository;

import com.hunkyhsu.ragagent.entity.Conversation;
import com.hunkyhsu.ragagent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserOrderByCreatedTimeDesc(User user);
    Optional<Conversation> findByIdAndUser(Long id, User user);
}
