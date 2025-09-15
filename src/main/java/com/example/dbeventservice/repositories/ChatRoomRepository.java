package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Enum.ChatType;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByTypeAndEventIdAndUserAAndUserB(ChatType type, Long eventId, User userA, User userB);

    Optional<ChatRoom> findByTypeAndResalePostIdAndBuyer(ChatType type, Long resalePostId, User buyer);

    Optional<ChatRoom> findByTypeAndEventId(ChatType type, Long eventId);

    Set<ChatRoom> findByArchivedFalseAndMembers_User_Id(Long userId);
}
