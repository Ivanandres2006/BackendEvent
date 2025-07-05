package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByBuyerOrSeller(User buyer, User seller);
    Optional<ChatRoom> findByResalePostAndBuyer(ResalePost post, User buyer);
}
