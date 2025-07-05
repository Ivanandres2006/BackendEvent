package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.ResalePost;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResalePostDTO {
    private Long id;
    private Long ticketId;
    private Double price;
    private String title;
    private String description;
    private String sellerName;
    private String eventTitle;
    private String eventImageUrl;
    private int unreadMessages = 0;
    private Long eventId; // ✅ ADD THIS

    public ResalePostDTO(ResalePost post) {
        this.id = post.getId();
        this.ticketId = post.getTicket().getId();
        this.price = post.getPrice();
        this.title = post.getTitle();
        this.description = post.getDescription();
        this.sellerName = post.getSeller().getFirstName();
        this.eventTitle = post.getTicket().getEvent().getTitle();
        this.eventImageUrl = post.getTicket().getEvent().getImageUrl();
        this.eventId = post.getTicket().getEvent().getId(); // ✅ SET THIS
    }
}
