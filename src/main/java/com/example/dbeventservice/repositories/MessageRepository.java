package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoomOrderByTimestampAsc(ChatRoom chatRoom);

    List<Message> findByChatRoomAndIsReadFalse(ChatRoom chatRoom);

    List<Message> findByChatRoom(ChatRoom chatRoom);
}
