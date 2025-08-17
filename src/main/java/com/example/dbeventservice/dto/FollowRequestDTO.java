package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class FollowRequestDTO {
    private Long followerId;
    private String username;
    private String displayName; // e.g., "First Last"
    private String requestedAt; // ISO string
}
