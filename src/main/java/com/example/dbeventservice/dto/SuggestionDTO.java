package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.User;
import lombok.Getter;

@Getter
public class SuggestionDTO {
    private final Long id;
    private final String username;
    private final String avatarUrl;
    private final String reason;   // "in_contacts" | "mutual_follows"
    private final int mutualCount; // 0 for contacts-based

    public SuggestionDTO(Long id, String username, String avatarUrl, String reason, int mutualCount) {
        this.id = id; this.username = username; this.avatarUrl = avatarUrl;
        this.reason = reason; this.mutualCount = mutualCount;
    }

    public static SuggestionDTO fromContacts(User u) {
        return new SuggestionDTO(u.getId(), u.getUsername(), u.getAvatarUrl(), "in_contacts", 0);
    }
    public static SuggestionDTO fromMutuals(User u, int mutualCount) {
        return new SuggestionDTO(u.getId(), u.getUsername(), u.getAvatarUrl(), "mutual_follows", mutualCount);
    }
}
