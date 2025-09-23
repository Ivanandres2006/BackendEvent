package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.ChatMessageDTO;
import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.ChatMemberRole;
import com.example.dbeventservice.model.Enum.ChatType;
import com.example.dbeventservice.model.Enum.MessageType;
import com.example.dbeventservice.repositories.ChatMemberRepository;
import com.example.dbeventservice.repositories.ChatMessageRepository;
import com.example.dbeventservice.repositories.ChatRoomRepository;
import com.example.dbeventservice.repositories.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRoomRepository roomRepo;
    private final ChatMemberRepository memberRepo;
    private final ChatMessageRepository msgRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate broker;
    private final NotificationService notificationService;

    public ChatService(ChatRoomRepository roomRepo,
                       ChatMemberRepository memberRepo,
                       ChatMessageRepository msgRepo,
                       UserRepository userRepo,
                       SimpMessagingTemplate broker,
                       NotificationService notificationService) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
        this.msgRepo = msgRepo;
        this.userRepo = userRepo;
        this.broker = broker;
        this.notificationService = notificationService;
    }


    /* ====================== CREATE / GET ROOMS ====================== */

    @Transactional
    public ChatRoom getOrCreateDirect(Long eventId, User a, User b) {
        // order users for uniqueness
        User userA = a.getId() < b.getId() ? a : b;
        User userB = a.getId() < b.getId() ? b : a;

        return roomRepo.findByTypeAndEventIdAndUserAAndUserB(ChatType.DIRECT, eventId, userA, userB)
                .orElseGet(() -> {
                    ChatRoom r = new ChatRoom();
                    r.setType(ChatType.DIRECT);
                    r.setEventId(eventId);
                    r.setUserA(userA);
                    r.setUserB(userB);
                    r.setCreatedBy(a);
                    r.setCreatedAt(Instant.now());
                    r.setUpdatedAt(Instant.now());
                    ChatRoom saved = roomRepo.save(r);
                    addMember(saved, a, ChatMemberRole.OWNER);
                    addMember(saved, b, ChatMemberRole.MEMBER);
                    return saved;
                });
    }

    @Transactional
    public ChatRoom getOrCreateResale(Long resalePostId, User buyer, User seller) {
        return roomRepo.findByTypeAndResalePostIdAndBuyer(ChatType.RESALE, resalePostId, buyer)
                .orElseGet(() -> {
                    ChatRoom r = new ChatRoom();
                    r.setType(ChatType.RESALE);
                    r.setResalePostId(resalePostId);
                    r.setBuyer(buyer);
                    r.setCreatedBy(buyer);
                    r.setCreatedAt(Instant.now());
                    r.setUpdatedAt(Instant.now());
                    ChatRoom saved = roomRepo.save(r);
                    addMember(saved, buyer, ChatMemberRole.OWNER);
                    addMember(saved, seller, ChatMemberRole.MEMBER);
                    return saved;
                });
    }

    @Transactional
    public ChatRoom getOrCreatePromoterGroup(Long eventId, User owner, List<User> promoters,
                                             String eventTitle, String eventIconUrl) {
        Optional<ChatRoom> existing = roomRepo.findByTypeAndEventId(ChatType.PROMOTER_GROUP, eventId);
        ChatRoom r = existing.orElseGet(() -> {
            ChatRoom x = new ChatRoom();
            x.setType(ChatType.PROMOTER_GROUP);
            x.setEventId(eventId);
            x.setCreatedBy(owner);
            x.setCreatedAt(Instant.now());
            x.setUpdatedAt(Instant.now());
            return roomRepo.save(x);
        });

        boolean dirty = false;
        if (eventTitle != null && !eventTitle.isBlank() && !eventTitle.equals(r.getName())) {
            r.setName(eventTitle.trim());           // <-- name = event title
            dirty = true;
        }
        if (eventIconUrl != null && !eventIconUrl.isBlank() && !Objects.equals(eventIconUrl, r.getIconUrl())) {
            r.setIconUrl(eventIconUrl.trim());      // optional: show event cover
            dirty = true;
        }
        if (dirty) r = roomRepo.save(r);

        addMember(r, owner, ChatMemberRole.OWNER);
        for (User p : promoters) addMember(r, p, ChatMemberRole.MEMBER);
        return r;
    }

    @Transactional
    public void addMember(ChatRoom room, User user, ChatMemberRole role) {
        if (memberRepo.findByChatAndUser(room, user).isPresent()) return;
        ChatMember m = new ChatMember();
        m.setChat(room);
        m.setUser(user);
        m.setRole(role == null ? ChatMemberRole.MEMBER : role);
        m.setJoinedAt(Instant.now());
        memberRepo.save(m);
    }

    /* ====================== RENAME / ICON ====================== */

    @Transactional
    public ChatRoom updateRoomMeta(ChatRoom room, String name, String iconUrl, User actor) {
        // Only OWNER or ADMIN in this room can update
        ChatMember self = memberRepo.findByChatAndUser(room, actor)
                .orElseThrow(() -> new RuntimeException("Not a member"));
        if (self.getRole() == ChatMemberRole.MEMBER) {
            throw new RuntimeException("Forbidden: requires OWNER or ADMIN");
        }
        if (name != null && !name.isBlank()) room.setName(name.trim());
        if (iconUrl != null && !iconUrl.isBlank()) room.setIconUrl(iconUrl.trim());
        room.setUpdatedAt(Instant.now());
        return roomRepo.save(room);
    }

    /* ====================== MESSAGES ====================== */

    @Transactional
    public ChatMessage sendText(ChatRoom room, User sender, String text, String metaJson) {
        ensureMember(room, sender);
        ChatMessage m = new ChatMessage();
        m.setChat(room);
        m.setSender(sender);
        m.setType(MessageType.TEXT);
        m.setText(text);
        m.setMetaJson(metaJson);
        m.setCreatedAt(Instant.now());
        ChatMessage saved = msgRepo.save(m);
        touchRoom(room, saved.getCreatedAt());
        publish(saved);
        notifyRoomMembers(room, saved);
        return saved;
    }

    @Transactional
    public ChatMessage sendImage(ChatRoom room, User sender, String imageUrl, String caption) {
        ensureMember(room, sender);
        ChatMessage m = new ChatMessage();
        m.setChat(room);
        m.setSender(sender);
        m.setType(MessageType.IMAGE);
        m.setAttachmentUrl(imageUrl);
        m.setText(caption);
        m.setCreatedAt(Instant.now());
        ChatMessage saved = msgRepo.save(m);
        touchRoom(room, saved.getCreatedAt());
        publish(saved);
        notifyRoomMembers(room, saved);
        return saved;
    }

    @Transactional
    public void markRead(ChatRoom room, User user) {
        ChatMember cm = ensureMember(room, user);
        cm.setLastReadAt(Instant.now());
        memberRepo.save(cm);
    }

    public List<ChatMessageDTO> getMessages(ChatRoom room, Integer limit, Long beforeId) {
        int pageSize = (limit == null || limit < 1 || limit > 200) ? 50 : limit;
        var pageable = PageRequest.of(0, pageSize);
        List<ChatMessage> msgs = (beforeId == null)
                ? msgRepo.findByChatOrderByIdDesc(room, pageable)
                : msgRepo.findByChatAndIdLessThanOrderByIdDesc(room, beforeId, pageable);
        Collections.reverse(msgs); // oldest → newest
        return msgs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long unreadCountFor(ChatRoom room, User user) {
        ChatMember cm = memberRepo.findByChatAndUser(room, user).orElse(null);
        if (cm == null) return 0;
        if (cm.getLastReadAt() == null) {
            // count all messages not sent by user
            return msgRepo.countByChatAndSenderNot(room, user);
        }
        return msgRepo.countByChatAndCreatedAtAfterAndSenderNot(room, cm.getLastReadAt(), user);
    }

    /* ====================== LIST ====================== */

    public List<ChatRoomSummaryDTO> myRooms(User me) {
        Set<ChatRoom> rooms = roomRepo.findByArchivedFalseAndMembers_User_Id(me.getId());
        return rooms.stream().map(r -> {
            ChatRoomSummaryDTO dto = new ChatRoomSummaryDTO();
            dto.id = r.getId();
            dto.type = r.getType();
            dto.iconUrl = roomIconFor(r, me);
            dto.displayName = roomNameFor(r, me);
            dto.lastMessageAt = r.getLastMessageAt();
            dto.unreadCount = unreadCountFor(r, me);
            dto.lastMessagePreview = lastPreview(r);
            return dto;
        }).sorted(Comparator
                .comparing((ChatRoomSummaryDTO x) -> x.unreadCount > 0 ? 0 : 1) // unread first
                .thenComparing((ChatRoomSummaryDTO x) -> x.lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
        ).collect(Collectors.toList());
    }

    /* ====================== HELPERS ====================== */

    private ChatMember ensureMember(ChatRoom room, User user) {
        return memberRepo.findByChatAndUser(room, user)
                .orElseThrow(() -> new RuntimeException("Not a member of this room"));
    }

    private void touchRoom(ChatRoom room, Instant when) {
        room.setLastMessageAt(when);
        room.setUpdatedAt(Instant.now());
        roomRepo.save(room);
    }

    private void publish(ChatMessage m) {
        ChatMessageDTO dto = toDTO(m);
        broker.convertAndSend("/topic/chat/" + m.getChat().getId(), dto);
    }

    private ChatMessageDTO toDTO(ChatMessage m) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.id = m.getId();
        dto.chatId = m.getChat().getId();
        dto.type = m.getType();
        dto.text = m.getText();
        dto.attachmentUrl = m.getAttachmentUrl();
        dto.metaJson = m.getMetaJson();
        dto.createdAt = m.getCreatedAt();
        if (m.getSender() != null) {
            dto.senderId = m.getSender().getId();
            dto.senderUsername = m.getSender().getUsername();
        }
        return dto;
    }

    private String roomNameFor(ChatRoom r, User me) {
        if (r.getType() == ChatType.DIRECT) {
            User other = Objects.equals(r.getUserA().getId(), me.getId()) ? r.getUserB() : r.getUserA();
            return other.getUsername();
        }
        if (r.getName() != null && !r.getName().isBlank()) return r.getName();
        return switch (r.getType()) {
            case RESALE -> "Resale chat";
            case PROMOTER_GROUP -> "Promoters";
            case ADMIN -> "Admin Support";
            default -> "Chat";
        };
    }

    private String roomIconFor(ChatRoom r, User me) {
        if (r.getType() == ChatType.DIRECT) {
            User other = Objects.equals(r.getUserA().getId(), me.getId()) ? r.getUserB() : r.getUserA();
            return other.getAvatarUrl(); // assumes your User has avatarUrl
        }
        return r.getIconUrl();
    }

    private String lastPreview(ChatRoom r) {
        var page = PageRequest.of(0, 1);
        List<ChatMessage> latest = msgRepo.findByChatOrderByIdDesc(r, page);
        if (latest.isEmpty()) return null;
        ChatMessage m = latest.get(0);
        return switch (m.getType()) {
            case TEXT -> m.getText();
            case IMAGE -> "[image]";
            case OFFER -> "[offer]";
            case SYSTEM -> m.getText() != null ? m.getText() : "[system]";
        };
    }

    private void notifyRoomMembers(ChatRoom room, ChatMessage msg) {
        // find all members except sender
        List<ChatMember> members = memberRepo.findByChat(room);
        for (ChatMember cm : members) {
            User recipient = cm.getUser();
            if (!Objects.equals(recipient.getId(), msg.getSender().getId())) {
                String title = "💬 New message";
                String body  = msg.getSender().getUsername() + ": " +
                        (msg.getType() == MessageType.TEXT ? msg.getText() : "[image]");

                Map<String,String> data = new HashMap<>();
                data.put("type", "CHAT");
                data.put("chatId", String.valueOf(room.getId()));
                data.put("senderId", String.valueOf(msg.getSender().getId()));
                data.put("senderUsername", msg.getSender().getUsername());

                notificationService.sendPushToUsernameWithData(recipient.getUsername(), title, body, data);
            }
        }
    }

}
