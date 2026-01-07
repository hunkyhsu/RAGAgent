package com.hunkyhsu.ragagent.repository;

import com.hunkyhsu.ragagent.entity.Conversation;
import com.hunkyhsu.ragagent.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationOrderByCreatedTimeAsc(Conversation conversation);
}
