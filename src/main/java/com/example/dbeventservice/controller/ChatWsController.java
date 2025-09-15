package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.repositories.ChatRoomRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.ChatService;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final ChatRoomRepository roomRepo;
    private final UserRepository userRepo;

    public ChatWsController(ChatService chatService, ChatRoomRepository roomRepo, UserRepository userRepo) {
        this.chatService = chatService;
        this.roomRepo = roomRepo;
        this.userRepo = userRepo;
    }

    public record SendTextPayload(String text) {}

    @MessageMapping("/chat/{roomId}/send-text")
    public void sendText(Authentication auth, @DestinationVariable Long roomId, SendTextPayload payload) {
        UserDetailsImpl principal = (UserDetailsImpl) auth.getPrincipal();
        var user = userRepo.findById(principal.getId()).orElseThrow();
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        chatService.sendText(room, user, payload.text(), null);
    }
}
