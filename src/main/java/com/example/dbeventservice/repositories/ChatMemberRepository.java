package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatMember;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
    List<ChatMember> findByChat(ChatRoom chat);
    Optional<ChatMember> findByChatAndUser(ChatRoom chat, User user);
    List<ChatMember> findByUser(User user);
}
