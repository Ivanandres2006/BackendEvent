// src/main/java/com/example/dbeventservice/dto/ChatRoomSummaryDTO.java
package com.example.dbeventservice.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRoomSummaryDTO {
    private Long chatRoomId;
    private Long resalePostId;
    private String title;
    private String sellerName;
    private int unreadMessages;
}
