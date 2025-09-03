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
    private Long sellerId;
    private Long eventId; // ✅ ADD THIS
    private boolean sold; // ✅ Add this
    private boolean paused;  // ✅ let the app hide paused posts immediately if needed

    // 👇 NEW
    public String sellerZelleEmail;
    public String sellerPagoMovilPhone;
    public String sellerPagoMovilCi;
    public String sellerPagoMovilBank;
    public String sellerCashNote;


    public ResalePostDTO(ResalePost post) {
        this.id = post.getId();
        this.ticketId = post.getTicket().getId();
        this.price = post.getPrice();
        this.title = post.getTitle();
        this.description = post.getDescription();
        this.sellerName = post.getSeller().getFirstName();
        this.eventTitle = post.getTicket().getEvent().getTitle();
        this.eventImageUrl = post.getTicket().getEvent().getImageUrl();
        this.sellerId = post.getSeller().getId();
        this.eventId = post.getTicket().getEvent().getId();
        this.sold = post.isSold();
        this.paused = post.isPaused();

        this.sellerZelleEmail     = post.getSellerZelleEmail();    // ← from post, not account email
        this.sellerPagoMovilPhone = post.getSellerPagoMovilPhone();
        this.sellerPagoMovilCi    = post.getSellerPagoMovilCi();
        this.sellerPagoMovilBank  = post.getSellerPagoMovilBank();
        this.sellerCashNote       = post.getSellerCashNote();

    }
}
