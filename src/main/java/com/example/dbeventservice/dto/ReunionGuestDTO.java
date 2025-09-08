package com.example.dbeventservice.dto;


import com.example.dbeventservice.model.ReunionGuest;

public class ReunionGuestDTO {
    private String email;
    private Boolean attending;

    public ReunionGuestDTO() {}

    public ReunionGuestDTO(String email, Boolean attending) {
        this.email = email;
        this.attending = attending;
    }

    public static ReunionGuestDTO fromEntity(ReunionGuest g) {
        return new ReunionGuestDTO(g.getEmail(), g.isAttending());
    }

    public String getEmail() { return email; }
    public Boolean getAttending() { return attending; }
    public void setEmail(String email) { this.email = email; }
    public void setAttending(Boolean attending) { this.attending = attending; }
}

