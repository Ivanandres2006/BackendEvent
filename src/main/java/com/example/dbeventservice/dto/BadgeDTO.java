package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class BadgeDTO {
    private String key;
    private String name;
    private String iconUrl;
    private String description;
    private String awardedAt; // ISO string; null when listing catalog only
}
