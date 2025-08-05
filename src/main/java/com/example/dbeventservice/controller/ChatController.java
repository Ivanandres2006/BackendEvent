package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.dto.CounterOfferRequest;
import com.example.dbeventservice.dto.MessageDTO;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Message;
import com.example.dbeventservice.service.ChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    // ✅ Create or get a chat room between buyer and seller
    @PostMapping("/start")
    public ResponseEntity<?> startChat(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody StartChatRequest request) {

        if (request.getResalePostId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "resalePostId is required"));
        }

        ChatRoom room = chatService.createOrGetChatRoom(request.getResalePostId(), userDetails.getId());
        return ResponseEntity.ok(room);
    }

    @PostMapping("/start-with-admin")
    public ResponseEntity<?> startAdminChat(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            Long userId = userDetails.getId();
            ChatRoom room = chatService.createOrFetchAdminChat(userId);
            return ResponseEntity.ok(Map.of("chatRoomId", room.getId())); // ✅ Proper JSON
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start chat with admin"));
        }
    }





    @GetMapping("/my-rooms")
    public ResponseEntity<List<ChatRoom>> getUserChatRooms(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<ChatRoom> rooms = chatService.getAllUserChatRooms(userDetails.getId());
        return ResponseEntity.ok(rooms);
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
                message.getTimestamp(),
                null,   // offerId
                null,   // amount
                null,
                message.getSender().getUsername() // ✅ Correct way
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

    @GetMapping("/image/{filename:.+}")
    public ResponseEntity<Resource> getChatImage(
            @PathVariable String filename,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        try {
            // ✅ Validate that the user is in a chat that references this image
            if (!chatService.userCanAccessImage(userDetails.getId(), filename)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Path imagePath = Paths.get("chat-images").resolve(filename).normalize();
            Resource resource = new UrlResource(imagePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/start-direct")
    public ResponseEntity<Map<String, Long>> startDirectChat(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody Map<String, Long> request) {
        Long eventId = request.get("eventId");
        ChatRoom room = chatService.createOrFetchDirectChat(user.getId(), eventId);
        return ResponseEntity.ok(Map.of("id", room.getId()));
    }


    @GetMapping("/promoter-group/{eventId}")
    public ResponseEntity<ChatRoom> getPromoterGroupChat(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long eventId) {
        return ResponseEntity.ok(chatService.createOrFetchPromoterGroupChat(user.getId(), eventId));
    }


    @PostMapping("/{chatRoomId}/counteroffer")
    public ResponseEntity<?> createCounterOffer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long chatRoomId,
            @RequestBody CounterOfferRequest request
    ) {
        try {
            // ✅ PREVENT duplicate offers: only allow one pending per sender in this chat
            boolean alreadyPending = chatService.hasPendingOffer(chatRoomId, userDetails.getId());
            if (alreadyPending) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of("error", "You already have a pending counteroffer.")
                );
            }

            var offer = chatService.createCounterOffer(chatRoomId, userDetails.getId(), request.getAmount());

            // Optional: Also create a message
            chatService.sendMessage(chatRoomId, userDetails.getId(), "Counteroffer: $" + String.format("%.2f", request.getAmount()));

            return ResponseEntity.ok(Map.of(
                    "offerId", offer.getId(),
                    "status", offer.getStatus(),
                    "amount", offer.getAmount()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadChatImage(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String filename = chatService.saveChatImage(file);
            return ResponseEntity.ok(filename);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed");
        }
    }

    @PostMapping("/counteroffer/{offerId}/accept")
    public ResponseEntity<?> acceptCounterOffer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long offerId
    ) {
        try {
            chatService.acceptCounterOffer(offerId, userDetails.getId());
            return ResponseEntity.ok(Map.of("message", "Counteroffer accepted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/counteroffer/{offerId}/decline")
    public ResponseEntity<?> declineCounterOffer(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long offerId
    ) {
        try {
            chatService.declineCounterOffer(offerId, userDetails.getId());
            return ResponseEntity.ok(Map.of("message", "Counteroffer declined"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }


    // ✅ Seller accepts buyer
    @PostMapping("/{chatRoomId}/accept")
    public ResponseEntity<?> acceptBuyer(@PathVariable Long chatRoomId) {
        chatService.acceptBuyer(chatRoomId);
        return ResponseEntity.ok("Buyer accepted");
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
