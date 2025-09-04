package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.dto.MessageDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.model.Enum.ChatType;
import com.example.dbeventservice.model.Enum.OfferStatus;
import com.example.dbeventservice.model.Enum.Role;
import com.example.dbeventservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ChatService {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResalePostRepository resalePostRepository;
    @Autowired private CounterOfferRepository counterOfferRepository;
    @Autowired private EventRepository eventRepository;

    // ✅ Use PromoterEventRepository instead of PromoterRepository for event↔promoter relations
    @Autowired private PromoterEventRepository promoterEventRepository;

    public ChatRoom createOrGetChatRoom(Long resalePostId, Long buyerId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        if (post.getSeller().getId().equals(buyerId)) {
            throw new IllegalArgumentException("Cannot chat with yourself");
        }

        return chatRoomRepository.findByResalePostAndBuyer(post, buyer)
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .resalePost(post)
                            .buyer(buyer)
                            .seller(post.getSeller())
                            .type(ChatType.RESALE)
                            .build();
                    return chatRoomRepository.save(room);
                });
    }

    public Message sendMessage(Long chatRoomId, Long senderId, String content) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        if (room.getType() == ChatType.RESALE
                && room.getBuyer() != null
                && room.getSeller() != null
                && room.getBuyer().getId().equals(room.getSeller().getId())) {
            throw new IllegalStateException("Invalid chat room (buyer == seller)");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        if (room.getType() == ChatType.RESALE) {
            if (!sender.equals(room.getBuyer()) && !sender.equals(room.getSeller())) {
                throw new IllegalArgumentException("User not part of this resale chat");
            }
        } else {
            if (room.getParticipants() == null || !room.getParticipants().contains(sender)) {
                throw new IllegalArgumentException("User not part of this group/direct chat");
            }
        }

        Message message = Message.builder()
                .chatRoom(room)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        return messageRepository.save(message);
    }

    public void acceptBuyer(Long chatRoomId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));
        room.setAccepted(true);
        chatRoomRepository.save(room);
    }

    public List<MessageDTO> getMessages(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (room.getType() == ChatType.RESALE) {
            if (room.getBuyer() == null || room.getSeller() == null) {
                throw new IllegalStateException("RESALE chat is missing buyer or seller");
            }
            if (!room.getBuyer().equals(user) && !room.getSeller().equals(user)) {
                throw new IllegalArgumentException("User not part of this resale chat");
            }
        } else {
            boolean isParticipant = room.getParticipants() != null &&
                    room.getParticipants().stream().anyMatch(u -> u.getId().equals(user.getId()));
            if (!isParticipant) {
                throw new IllegalArgumentException("User not a participant of this chat");
            }
        }

        List<Message> messages = messageRepository.findByChatRoomOrderByTimestampAsc(room);
        List<CounterOffer> offers = counterOfferRepository.findByChatRoom(room);

        boolean updated = false;
        for (Message message : messages) {
            if (!message.getSender().equals(user) && !message.isRead()) {
                message.setRead(true);
                updated = true;
            }
        }
        if (updated) messageRepository.saveAll(messages);

        List<MessageDTO> messageDTOs = messages.stream()
                .map(m -> MessageDTO.builder()
                        .id(m.getId())
                        .senderId(m.getSender().getId())
                        .senderName(m.getSender().getUsername())
                        .content(m.getContent())
                        .timestamp(m.getTimestamp())
                        .build())
                .toList();

        List<MessageDTO> offerDTOs = offers.stream()
                .map(o -> MessageDTO.builder()
                        .id(-1000L - o.getId())
                        .senderId(o.getSender().getId())
                        .content("Counteroffer: $" + String.format("%.2f", o.getAmount()))
                        .timestamp(o.getCreatedAt())
                        .offerId(o.getId().intValue())
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .build())
                .toList();

        return Stream.concat(messageDTOs.stream(), offerDTOs.stream())
                .sorted(Comparator.comparing(MessageDTO::getTimestamp))
                .toList();
    }

    public CounterOffer createCounterOffer(Long chatRoomId, Long senderId, double amount) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        if (!room.getBuyer().equals(sender) && !room.getSeller().equals(sender)) {
            throw new IllegalArgumentException("User not in this chat");
        }

        boolean hasPending = counterOfferRepository.findByChatRoom(room).stream()
                .anyMatch(o -> o.getSender().equals(sender) && o.getStatus() == OfferStatus.PENDING);
        if (hasPending) throw new IllegalArgumentException("You already have a pending counteroffer.");

        CounterOffer offer = CounterOffer.builder()
                .chatRoom(room)
                .sender(sender)
                .amount(amount)
                .status(OfferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return counterOfferRepository.save(offer);
    }

    public void acceptCounterOffer(Long offerId, Long userId) {
        CounterOffer offer = counterOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found"));

        if (!offer.getChatRoom().getSeller().getId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to accept this offer.");
        }

        offer.setStatus(OfferStatus.ACCEPTED);
        counterOfferRepository.save(offer);

        String acceptedTag = "[acceptedOffer]:" + offer.getChatRoom().getResalePost().getId() + ":" + offer.getAmount();

        Message message = Message.builder()
                .chatRoom(offer.getChatRoom())
                .sender(offer.getChatRoom().getSeller())
                .content(acceptedTag)
                .timestamp(LocalDateTime.now())
                .build();

        messageRepository.save(message);
    }

    public void declineCounterOffer(Long offerId, Long userId) {
        CounterOffer offer = counterOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found"));

        if (!offer.getChatRoom().getSeller().getId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to decline this offer.");
        }

        offer.setStatus(OfferStatus.DECLINED);
        counterOfferRepository.save(offer);

        Message message = Message.builder()
                .chatRoom(offer.getChatRoom())
                .sender(offer.getChatRoom().getSeller())
                .content("❌ Your offer of $" + offer.getAmount() + " was declined.")
                .timestamp(LocalDateTime.now())
                .build();
        messageRepository.save(message);
    }

    public boolean userCanAccessImage(Long userId, String filename) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ChatRoom> rooms = chatRoomRepository.findByBuyerOrSeller(user, user);
        for (ChatRoom room : rooms) {
            List<Message> messages = messageRepository.findByChatRoomOrderByTimestampAsc(room);
            for (Message m : messages) {
                if (m.getContent().contains("[img]" + filename)) return true;
            }
        }
        return false;
    }

    public boolean userHasAccessToImage(Long userId, String filename) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        List<ChatRoom> rooms = chatRoomRepository.findByBuyerOrSeller(user, user);
        for (ChatRoom room : rooms) {
            List<Message> messages = messageRepository.findByChatRoom(room);
            for (Message msg : messages) {
                if (msg.getContent().contains(filename)) return true;
            }
        }
        return false;
    }

    public String saveChatImage(MultipartFile file) throws IOException {
        String uploadsDir = "chat-images/";
        File dir = new File(uploadsDir);
        if (!dir.exists()) dir.mkdirs();
        String uniqueName = UUID.randomUUID().toString() + ".jpg";
        File destination = new File(dir, uniqueName);
        file.transferTo(destination);
        return uniqueName;
    }

    public int getUnreadMessageCount(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ChatRoom> rooms = chatRoomRepository.findByBuyerOrSeller(user, user);
        int unreadCount = 0;
        for (ChatRoom room : rooms) {
            List<Message> messages = messageRepository.findByChatRoomAndIsReadFalse(room);
            for (Message m : messages) {
                if (!m.getSender().equals(user)) unreadCount++;
            }
        }
        return unreadCount;
    }

    public List<ChatRoomSummaryDTO> getChatRoomSummaries(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();

        List<ChatRoom> resaleRooms = chatRoomRepository.findByBuyerOrSeller(user, user);
        List<ChatRoom> eventRooms  = chatRoomRepository.findByParticipantsContaining(user);

        List<ChatRoomSummaryDTO> resaleSummaries = resaleRooms.stream()
                .filter(r -> r.getType() == ChatType.RESALE)
                .map(room -> {
                    int unread = (int) messageRepository
                            .findByChatRoomAndIsReadFalse(room).stream()
                            .filter(m -> !m.getSender().equals(user))
                            .count();

                    Long otherUserId = room.getBuyer().getId().equals(userId)
                            ? room.getSeller().getId()
                            : room.getBuyer().getId();

                    return ChatRoomSummaryDTO.builder()
                            .chatRoomId(room.getId())
                            .resalePostId(room.getResalePost().getId())
                            .title(room.getResalePost().getTitle())
                            .buyerId(room.getBuyer().getId())
                            .sellerName(room.getSeller().getFirstName())
                            .unreadMessages(unread)
                            .type(room.getType().name())
                            .otherUserId(otherUserId)
                            .build();
                }).toList();

        List<ChatRoomSummaryDTO> eventSummaries = eventRooms.stream()
                .filter(r -> r.getType() != ChatType.RESALE)
                .map(room -> {
                    int unread = (int) messageRepository
                            .findByChatRoomAndIsReadFalse(room).stream()
                            .filter(m -> !m.getSender().equals(user))
                            .count();

                    Long otherUserId = room.getParticipants().stream()
                            .filter(u -> !u.getId().equals(userId))
                            .findFirst().map(User::getId).orElse(null);

                    String otherUsername = room.getParticipants().stream()
                            .filter(u -> !u.getId().equals(userId))
                            .findFirst().map(User::getUsername).orElse("unknown");

                    return ChatRoomSummaryDTO.builder()
                            .chatRoomId(room.getId())
                            .resalePostId(null)
                            .type(room.getType().name())
                            .title(room.getType() == ChatType.DIRECT ? "Chat with Organizer" : "Promoter Group Chat")
                            .buyerId(null)
                            .sellerName(null)
                            .unreadMessages(unread)
                            .eventId(room.getEventId())
                            .otherUserId(otherUserId)
                            .otherUsername(otherUsername)
                            .build();
                }).toList();

        return Stream.concat(resaleSummaries.stream(), eventSummaries.stream()).toList();
    }

    @Transactional
    public ChatRoom createOrFetchPromoterGroupChat(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        boolean isOwner = event.getCreatedBy().getId().equals(userId);
        boolean isPromoter = promoterEventRepository.existsByEvent_IdAndPromoter_User_Id(eventId, userId); // ✅ fixed

        if (!isOwner && !isPromoter) throw new AccessDeniedException("Not allowed");

        ChatRoom room = chatRoomRepository.findByEventIdAndType(eventId, ChatType.PROMOTER_GROUP).orElse(null);
        if (room == null) {
            room = new ChatRoom();
            room.setType(ChatType.PROMOTER_GROUP);
            room.setEventId(eventId);
            room.setParticipants(new HashSet<>());
        }

        Set<User> participants = new HashSet<>();
        participants.add(event.getCreatedBy());

        // ✅ pull promoters via PromoterEvent
        List<PromoterEvent> peList = promoterEventRepository.findByEvent(event);
        for (PromoterEvent pe : peList) {
            if (pe.getPromoter() != null && pe.getPromoter().getUser() != null) {
                participants.add(pe.getPromoter().getUser());
            }
        }

        room.setParticipants(participants);
        chatRoomRepository.saveAndFlush(room);

        return room;
    }

    @Transactional
    public void refreshPromoterGroupParticipants(Long eventId) {
        ChatRoom room = chatRoomRepository.findByEventIdAndType(eventId, ChatType.PROMOTER_GROUP).orElse(null);
        if (room == null) return;

        Event event = eventRepository.findById(eventId).orElseThrow();

        Set<User> participants = new HashSet<>();
        participants.add(event.getCreatedBy());

        List<PromoterEvent> peList = promoterEventRepository.findByEvent(event);
        for (PromoterEvent pe : peList) {
            if (pe.getPromoter() != null && pe.getPromoter().getUser() != null) {
                participants.add(pe.getPromoter().getUser());
            }
        }

        room.setParticipants(participants);
        chatRoomRepository.saveAndFlush(room);
    }

    public List<ChatRoom> getAllUserChatRooms(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        Set<ChatRoom> allRooms = new HashSet<>();
        allRooms.addAll(chatRoomRepository.findByBuyerOrSeller(user, user));
        allRooms.addAll(chatRoomRepository.findByParticipantsContaining(user));
        return new ArrayList<>(allRooms);
    }

    public boolean hasPendingOffer(Long chatRoomId, Long senderId) {
        return counterOfferRepository.existsByChatRoomIdAndSenderIdAndStatus(chatRoomId, senderId, "PENDING");
    }

    @Transactional
    public ChatRoom createOrFetchAdminChat(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<User> adminOpt = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst();
        if (adminOpt.isEmpty()) throw new IllegalStateException("No admin found");

        User admin = adminOpt.get();
        if (user.getId().equals(admin.getId())) {
            throw new IllegalArgumentException("Cannot chat with yourself");
        }

        Optional<ChatRoom> existing = chatRoomRepository.findDirectChat(null, userId, admin.getId());
        if (existing.isPresent()) return existing.get();

        ChatRoom room = new ChatRoom();
        room.setType(ChatType.DIRECT);
        room.setEventId(null);

        Set<User> participants = new HashSet<>();
        participants.add(user);
        participants.add(admin);
        room.setParticipants(participants);
        chatRoomRepository.save(room);

        boolean hasMessages = messageRepository.findByChatRoom(room).size() > 0;
        if (!hasMessages) {
            Message welcome = Message.builder()
                    .chatRoom(room)
                    .sender(admin)
                    .content("Hola 👋, ¿en qué te puedo ayudar?")
                    .timestamp(LocalDateTime.now())
                    .build();
            messageRepository.save(welcome);
        }

        return room;
    }

    public ChatRoom createOrFetchDirectChat(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User owner = event.getCreatedBy();
        if (user.getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Cannot chat with yourself");
        }

        return chatRoomRepository.findDirectChat(eventId, userId, owner.getId())
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setType(ChatType.DIRECT);
                    room.setEventId(eventId);

                    Set<User> participants = new HashSet<>();
                    participants.add(user);
                    participants.add(owner);
                    room.setParticipants(participants);

                    return chatRoomRepository.save(room);
                });
    }

    public List<ChatRoom> getUserChatRooms(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return chatRoomRepository.findByBuyerOrSeller(user, user);
    }
}
