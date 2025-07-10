package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.CounterOffer;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Enum.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CounterOfferRepository extends JpaRepository<CounterOffer, Long> {
    List<CounterOffer> findByChatRoom(ChatRoom chatRoom);

    Optional<CounterOffer> findTopByChatRoomAndSenderIdAndStatusOrderByCreatedAtDesc(
            ChatRoom chatRoom,
            Long senderId,
            OfferStatus status
    );

    boolean existsByChatRoomIdAndSenderIdAndStatus(Long chatRoomId, Long senderId, String status);
}
