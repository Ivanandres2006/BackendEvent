package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ChatMessageDTO;
import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.dto.UpdateRoomRequest;
import com.example.dbeventservice.model.ChatRoom;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.model.Enum.ChatType;
import com.example.dbeventservice.repositories.ChatRoomRepository;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.UserRepository;
import com.example.dbeventservice.service.ChatService;
import com.example.dbeventservice.service.CloudinaryStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final CloudinaryStorageService storage;
    private final EventRepository eventRepo; // or EventService


    public ChatController(ChatService chatService,
                          ChatRoomRepository roomRepo,
                          UserRepository userRepo,
                          CloudinaryStorageService storage,
                          EventRepository eventRepo) {
        this.chatService = chatService;
        this.roomRepo = roomRepo;
        this.userRepo = userRepo;
        this.storage = storage;
        this.eventRepo = eventRepo;
    }

    private User me(UserDetailsImpl principal) {
        return userRepo.findById(principal.getId()).orElseThrow();
    }

    /* --------- ROOM LIST --------- */

    @GetMapping("/rooms/my")
    public List<ChatRoomSummaryDTO> myRooms(@AuthenticationPrincipal UserDetailsImpl principal) {
        return chatService.myRooms(me(principal));
    }

    /* --------- START ROOMS --------- */

    @PostMapping("/start-direct")
    public ResponseEntity<?> startDirect(@AuthenticationPrincipal UserDetailsImpl principal,
                                         @RequestParam Long eventId,
                                         @RequestParam Long otherUserId) {
        User a = me(principal);
        User b = userRepo.findById(otherUserId).orElseThrow();
        ChatRoom room = chatService.getOrCreateDirect(eventId, a, b);
        return ResponseEntity.ok(Map.of("roomId", room.getId()));
    }

    @PostMapping("/start-resale")
    public ResponseEntity<?> startResale(@AuthenticationPrincipal UserDetailsImpl principal,
                                         @RequestParam Long resalePostId,
                                         @RequestParam Long sellerId) {
        User buyer = me(principal);
        User seller = userRepo.findById(sellerId).orElseThrow();
        ChatRoom room = chatService.getOrCreateResale(resalePostId, buyer, seller);
        return ResponseEntity.ok(Map.of("roomId", room.getId()));
    }

    @PostMapping("/promoter-group")
    public ResponseEntity<?> promoterGroup(@AuthenticationPrincipal UserDetailsImpl principal,
                                           @RequestParam Long eventId,
                                           @RequestBody List<Long> promoterUserIds) {
        User owner = me(principal);
        List<User> promoters = userRepo.findAllById(promoterUserIds);

        Event ev = eventRepo.findById(eventId).orElseThrow(); // get title/icon
        ChatRoom room = chatService.getOrCreatePromoterGroup(
                eventId, owner, promoters, ev.getTitle(), ev.getImageUrl());

        return ResponseEntity.ok(Map.of("id", room.getId())); // (id) decodes everywhere
    }


    /* --------- ROOM SETTINGS --------- */

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<?> updateRoom(@AuthenticationPrincipal UserDetailsImpl principal,
                                        @PathVariable Long roomId,
                                        @RequestBody UpdateRoomRequest body) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        ChatRoom updated = chatService.updateRoomMeta(room, body.name, body.iconUrl, me(principal));
        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "name", updated.getName(),
                "iconUrl", updated.getIconUrl()
        ));
    }

    /* --------- MESSAGES --------- */

    @GetMapping("/rooms/{roomId}/messages")
    public List<ChatMessageDTO> getMessages(@AuthenticationPrincipal UserDetailsImpl principal,
                                            @PathVariable Long roomId,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) Long beforeId) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        return chatService.getMessages(room, limit, beforeId);
    }

    @PostMapping("/rooms/{roomId}/messages/text")
    public ResponseEntity<?> sendText(@AuthenticationPrincipal UserDetailsImpl principal,
                                      @PathVariable Long roomId,
                                      @RequestParam String text) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        var msg = chatService.sendText(room, me(principal), text, null);
        return ResponseEntity.ok(msg.getId());
    }

    @PostMapping(value = "/rooms/{roomId}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendImage(@AuthenticationPrincipal UserDetailsImpl principal,
                                       @PathVariable Long roomId,
                                       @RequestPart("file") MultipartFile file,
                                       @RequestPart(value = "caption", required = false) String caption) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        String url = storage.uploadChatImage(file, roomId, me(principal).getId());
        var msg = chatService.sendImage(room, me(principal), url, caption);
        return ResponseEntity.ok(Map.of("messageId", msg.getId(), "url", url));
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<?> markRead(@AuthenticationPrincipal UserDetailsImpl principal,
                                      @PathVariable Long roomId) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        chatService.markRead(room, me(principal));
        return ResponseEntity.ok().build();
    }

    /* --------- GLOBAL UNREAD --------- */

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@AuthenticationPrincipal UserDetailsImpl principal) {
        var list = chatService.myRooms(me(principal));
        long total = list.stream().mapToLong(x -> x.unreadCount).sum();
        return Map.of("total", total);
    }
}
