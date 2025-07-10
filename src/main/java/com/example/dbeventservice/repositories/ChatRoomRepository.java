package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Enum.ChatType;
import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByBuyerOrSeller(User buyer, User seller);
    Optional<ChatRoom> findByResalePostAndBuyer(ResalePost post, User buyer);

    Optional<ChatRoom> findByResalePostIdAndBuyerId(Long resalePostId, Long buyerId);
    Optional<ChatRoom> findByEventIdAndType(Long eventId, ChatType type);

    @Query("SELECT c FROM ChatRoom c JOIN c.participants p1 JOIN c.participants p2 " +
            "WHERE c.type = 'DIRECT' AND c.eventId = :eventId AND p1.id = :userId AND p2.id = :ownerId")
    Optional<ChatRoom> findDirectChat(Long eventId, Long userId, Long ownerId);

    List<ChatRoom> findByParticipantsContaining(User user);

}
