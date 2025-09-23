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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.dbeventservice.repositories.ResalePostRepository;   // ← add
import com.example.dbeventservice.model.ResalePost;                     // ← add



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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResalePostRepository resalePostRepo;   // ← add


    public ChatService(ChatRoomRepository roomRepo,
                       ChatMemberRepository memberRepo,
                       ChatMessageRepository msgRepo,
                       UserRepository userRepo,
                       SimpMessagingTemplate broker,
                       NotificationService notificationService,
                       ResalePostRepository resalePostRepo) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
        this.msgRepo = msgRepo;
        this.userRepo = userRepo;
        this.broker = broker;
        this.notificationService = notificationService;
        this.resalePostRepo = resalePostRepo;
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
            r.setName(eventTitle.trim());
            dirty = true;
        }
        if (eventIconUrl != null && !eventIconUrl.isBlank() && !Objects.equals(eventIconUrl, r.getIconUrl())) {
            r.setIconUrl(eventIconUrl.trim());
            dirty = true;
        }
        if (dirty) r = roomRepo.save(r);

        // Track who got added right now (idempotent)
        List<String> newlyAddedUsernames = new ArrayList<>();

        if (memberRepo.findByChatAndUser(r, owner).isEmpty()) {
            addMember(r, owner, ChatMemberRole.OWNER);
            // Usually skip notifying the owner about themselves; toggle if you want:
            // newlyAddedUsernames.add(owner.getUsername());
        }
        for (User p : promoters) {
            if (memberRepo.findByChatAndUser(r, p).isEmpty()) {
                addMember(r, p, ChatMemberRole.MEMBER);
                if (p.getUsername() != null) newlyAddedUsernames.add(p.getUsername());
            }
        }

        // 🔔 Notify only the newly added users
        if (!newlyAddedUsernames.isEmpty()) {
            String title = "👥 Promoter group";
            String body  = "You were added to " + (eventTitle != null ? ("\"" + eventTitle + "\"") : "an event") + " promoter chat";

            Map<String,String> data = new HashMap<>();
            data.put("type", "CHAT");                    // iOS can branch on this
            data.put("kind", "PROMOTER_GROUP_JOIN");
            data.put("chatId", String.valueOf(r.getId()));
            data.put("eventId", String.valueOf(eventId));

            notificationService.sendPushToUsernamesWithData(newlyAddedUsernames, title, body, data);
        }

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

    // ChatService.java (add near sendText/sendImage)

    @Transactional
    public ChatMessage sendOffer(ChatRoom room, User sender, double amount, String currency, Long parentOfferId) {
        ensureMember(room, sender);

        String cur = (currency == null || currency.isBlank()) ? "USD" : currency.toUpperCase(); // <-- add

        var payload = new java.util.HashMap<String,Object>();
        payload.put("kind", "OFFER");
        payload.put("amount", amount);
        payload.put("currency", cur);                  // <-- use cur
        payload.put("authorId", sender.getId());
        payload.put("status", "PENDING");
        payload.put("parentId", parentOfferId == null ? 0L : parentOfferId);
        if (room.getType() == ChatType.RESALE && room.getResalePostId() != null) {
            payload.put("resalePostId", room.getResalePostId());
        }

        ChatMessage m = new ChatMessage();
        m.setChat(room);
        m.setSender(sender);
        m.setType(MessageType.OFFER);
        try {
            m.setMetaJson(JSON.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize offer payload", e);
        }
        m.setCreatedAt(Instant.now());

        ChatMessage saved = msgRepo.save(m);
        touchRoom(room, saved.getCreatedAt());
        publish(saved);

        var otherUsernames = memberRepo.findByChat(room).stream()
                .map(ChatMember::getUser)
                .filter(u -> !Objects.equals(u.getId(), sender.getId()))
                .map(User::getUsername)
                .toList();

        Map<String,String> data = new HashMap<>();
        data.put("type","CHAT");
        data.put("chatId", String.valueOf(room.getId()));
        data.put("offerMessageId", String.valueOf(saved.getId()));
        data.put("amount", String.valueOf(amount));
        data.put("currency", cur);                     // <-- use cur

        notificationService.sendPushToUsernamesWithData(
                otherUsernames,
                "💸 New offer",
                sender.getUsername() + " offered " + cur + " " + amount,   // <-- use cur
                data
        );

        return saved;
    }

    @Transactional
    public ChatMessage setOfferStatus(ChatRoom room, User actor, Long offerMessageId, String newStatus) {
        ensureMember(room, actor);

        ChatMessage offer = msgRepo.findById(offerMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found"));
        if (!Objects.equals(offer.getChat().getId(), room.getId())) throw new IllegalArgumentException("Wrong room");
        if (offer.getType() != MessageType.OFFER) throw new IllegalArgumentException("Not an offer");

        // ✅ Only ACCEPTED or DECLINED
        if (!"ACCEPTED".equals(newStatus) && !"DECLINED".equals(newStatus)) {
            throw new IllegalArgumentException("Invalid status");
        }

        // ✅ In RESALE chats, only the SELLER (not the buyer) may accept/decline
        if (room.getType() == ChatType.RESALE) {
            User buyer = room.getBuyer(); // may be null if your entity differs
            if (buyer != null && Objects.equals(buyer.getId(), actor.getId())) {
                throw new IllegalArgumentException("Only the seller can " + newStatus.toLowerCase());
            }
        }

        Map<String, Object> map;
        try {
            map = JSON.readValue(
                    Optional.ofNullable(offer.getMetaJson()).orElse("{}"),
                    java.util.Map.class
            );
        } catch (Exception e) {
            // If the stored JSON is bad, start fresh instead of failing the action.
            map = new HashMap<>();
        }
        map.put("status", newStatus);
        try {
            offer.setMetaJson(JSON.writeValueAsString(map));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize offer status", e);
        }
        msgRepo.save(offer);

        if ("ACCEPTED".equals(newStatus)
                && room.getType() == ChatType.RESALE
                && room.getResalePostId() != null) {

            // Pull accepted amount from the offer meta
            Double amount = null;
            Object a = map.get("amount");
            if (a instanceof Number n) {
                amount = n.doubleValue();
            } else if (a instanceof String s) {
                try { amount = Double.valueOf(s); } catch (NumberFormatException ignored) {}
            }

            if (amount != null) {
                Double finalAmount = amount;
                resalePostRepo.findById(room.getResalePostId()).ifPresent(post -> {
                    var seller = post.getSeller();
                    // Only the seller can lock the price
                    if (seller != null && Objects.equals(seller.getId(), actor.getId())) {
                        post.setPrice(finalAmount);
                        resalePostRepo.save(post);
                    }
                });
            }
        }

        ChatMessage sys = new ChatMessage();
        sys.setChat(room);
        sys.setType(MessageType.SYSTEM);
        sys.setText("Offer " + newStatus.toLowerCase());
        sys.setCreatedAt(Instant.now());
        msgRepo.save(sys);

        touchRoom(room, sys.getCreatedAt());
        publish(offer);
        publish(sys);

        User sender = offer.getSender();
        if (sender != null && !Objects.equals(sender.getId(), actor.getId())) {
            Map<String,String> data = new HashMap<>();
            data.put("type","CHAT");
            data.put("chatId", String.valueOf(room.getId()));
            data.put("offerMessageId", String.valueOf(offerMessageId));
            data.put("status", newStatus);
            notificationService.sendPushToUsernameWithData(
                    sender.getUsername(),
                    "ACCEPTED".equals(newStatus) ? "✅ Offer accepted" : "✋ Offer declined",
                    actor.getUsername() + " " + newStatus.toLowerCase() + " your offer",
                    data
            );
        }

        return offer;
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
