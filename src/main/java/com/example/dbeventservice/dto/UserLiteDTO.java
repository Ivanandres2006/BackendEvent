package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.User;

public class UserLiteDTO {
    private Long id;
    private String username;
    private String displayName;

    public UserLiteDTO() {}

    public UserLiteDTO(Long id, String username, String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }

    public static UserLiteDTO fromEntity(User u) {
        String display = (u.getFirstName() != null ? u.getFirstName() : "") +
                (u.getLastName() != null ? " " + u.getLastName() : "");
        display = display.isBlank() ? (u.getUsername() != null ? u.getUsername() : "User") : display;
        return new UserLiteDTO(u.getId(), u.getUsername(), display);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
