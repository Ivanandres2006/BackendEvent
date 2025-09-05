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

    @Query("""
    SELECT c FROM ChatRoom c
    WHERE c.type = 'DIRECT'
      AND c.eventId = :eventId
      AND EXISTS (
          SELECT 1 FROM c.participants u1 WHERE u1.id = :userId
      )
      AND EXISTS (
          SELECT 1 FROM c.participants u2 WHERE u2.id = :ownerId
      )
""")
    Optional<ChatRoom> findDirectChat(Long eventId, Long userId, Long ownerId);

    List<ChatRoom> findByParticipantsContaining(User user);

    // ChatRoomRepository.java
    @Query("""
select c from ChatRoom c
left join fetch c.participants p
where c.id = :id
""")
    Optional<ChatRoom> findWithParticipants(Long id);


}
