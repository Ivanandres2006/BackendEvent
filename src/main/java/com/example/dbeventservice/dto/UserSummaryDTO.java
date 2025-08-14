package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.User;
import lombok.Getter;

@Getter
public class UserSummaryDTO {
    private final Long id;
    private final String username;
    private final String avatarUrl;

    public UserSummaryDTO(Long id, String username, String avatarUrl) {
        this.id = id; this.username = username; this.avatarUrl = avatarUrl;
    }
    public static UserSummaryDTO from(User u) {
        return new UserSummaryDTO(u.getId(), u.getUsername(), u.getAvatarUrl());
    }
}
