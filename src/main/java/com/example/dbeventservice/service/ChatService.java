package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.ChatRoomSummaryDTO;
import com.example.dbeventservice.dto.MessageDTO;
import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResalePostRepository resalePostRepository;

    public ChatRoom createOrGetChatRoom(Long resalePostId, Long buyerId) {
        ResalePost post = resalePostRepository.findById(resalePostId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found"));

        return chatRoomRepository.findByResalePostAndBuyer(post, buyer)
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .resalePost(post)
                            .buyer(buyer)
                            .seller(post.getSeller())
                            .build();
                    return chatRoomRepository.save(room);
                });
    }

    public Message sendMessage(Long chatRoomId, Long senderId, String content) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        if (!sender.equals(room.getBuyer()) && !sender.equals(room.getSeller())) {
            throw new IllegalArgumentException("User not part of this chat");
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

        // Make sure both buyer and seller can fetch messages
        if (!room.getBuyer().equals(user) && !room.getSeller().equals(user)) {
            throw new IllegalArgumentException("User not part of this chat room");
        }

        List<Message> messages = messageRepository.findByChatRoomOrderByTimestampAsc(room);

        // Mark unread messages sent *by the other user* as read
        boolean updated = false;
        for (Message message : messages) {
            if (!message.getSender().equals(user) && !message.isRead()) {
                message.setRead(true);
                updated = true;
            }
        }

        if (updated) {
            messageRepository.saveAll(messages);
        }

        // Convert to DTO so frontend doesn’t break
        return messages.stream()
                .map(m -> new MessageDTO(m.getId(), m.getSender().getId(), m.getContent(), m.getTimestamp()))
                .toList();
    }

    public int getUnreadMessageCount(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ChatRoom> rooms = chatRoomRepository.findByBuyerOrSeller(user, user);

        int unreadCount = 0;
        for (ChatRoom room : rooms) {
            List<Message> messages = messageRepository.findByChatRoomAndIsReadFalse(room);
            for (Message m : messages) {
                if (!m.getSender().equals(user)) {
                    unreadCount++;
                }
            }
        }
        return unreadCount;
    }

    public List<ChatRoomSummaryDTO> getChatRoomSummaries(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ChatRoom> rooms = chatRoomRepository.findByBuyerOrSeller(user, user);

        return rooms.stream().map(room -> {
            int unread = (int) messageRepository
                    .findByChatRoomAndIsReadFalse(room).stream()
                    .filter(m -> !m.getSender().equals(user))
                    .count();

            return ChatRoomSummaryDTO.builder()
                    .chatRoomId(room.getId()) // ✅ Add this line
                    .resalePostId(room.getResalePost().getId())
                    .title(room.getResalePost().getTitle())
                    .sellerName(room.getSeller().getFirstName()) // or full name
                    .unreadMessages(unread)
                    .build();
        }).toList();
    }

    public List<ChatRoom> getUserChatRooms(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return chatRoomRepository.findByBuyerOrSeller(user, user);
    }
}
