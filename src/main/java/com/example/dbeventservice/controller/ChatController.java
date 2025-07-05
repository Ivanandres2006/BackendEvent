package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.dto.MessageDTO;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Message;
import com.example.dbeventservice.service.ChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    // ✅ Create or get a chat room between buyer and seller
    @PostMapping("/start")
    public ResponseEntity<ChatRoom> startChat(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody StartChatRequest request) {

        ChatRoom room = chatService.createOrGetChatRoom(request.resalePostId, userDetails.getId());
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{chatRoomId}/send")
    public ResponseEntity<MessageDTO> sendMessage(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long chatRoomId,
            @RequestBody SendMessageRequest request) {

        Message message = chatService.sendMessage(chatRoomId, userDetails.getId(), request.content);
        return ResponseEntity.ok(new MessageDTO(
                message.getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getTimestamp()
        ));
    }


    @GetMapping("/{chatRoomId}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long chatRoomId) {
        List<MessageDTO> messages = chatService.getMessages(chatRoomId, userDetails.getId());
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ChatRoomSummaryDTO>> getMyChats(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<ChatRoomSummaryDTO> chats = chatService.getChatRoomSummaries(userDetails.getId());
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        int count = chatService.getUnreadMessageCount(userDetails.getId());
        return ResponseEntity.ok(Map.of("unread", count));
    }

    // ✅ Seller accepts buyer
    @PostMapping("/{chatRoomId}/accept")
    public ResponseEntity<?> acceptBuyer(@PathVariable Long chatRoomId) {
        chatService.acceptBuyer(chatRoomId);
        return ResponseEntity.ok("Buyer accepted");
    }

    // ✅ Get all chat rooms for the current user
    @GetMapping("/my-rooms")
    public ResponseEntity<List<ChatRoom>> getUserChatRooms(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<ChatRoom> rooms = chatService.getUserChatRooms(userDetails.getId());
        return ResponseEntity.ok(rooms);
    }

    // DTOs
    @Getter
    @Setter
    static class StartChatRequest {
        @NotNull
        public Long resalePostId;
    }

    @Getter
    @Setter
    static class SendMessageRequest {
        @NotBlank
        public String content;
    }
}
