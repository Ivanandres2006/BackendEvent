package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatMessage;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatOrderByIdDesc(ChatRoom chat, Pageable pageable);

    List<ChatMessage> findByChatAndIdLessThanOrderByIdDesc(ChatRoom chat, Long beforeId, Pageable pageable);

    long countByChatAndCreatedAtAfterAndSenderNot(ChatRoom chat, Instant after, User sender);

    long countByChatAndSenderNot(ChatRoom chat, User sender);
}
