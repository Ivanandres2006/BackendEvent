package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.User;

public class UserMiniDTO {
    private Long id;
    private String email;

    public UserMiniDTO() {}

    public UserMiniDTO(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public static UserMiniDTO fromEntity(User u) {
        return new UserMiniDTO(u.getId(), u.getEmail());
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setId(Long id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
}

