package com.example.dbeventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class PhotoDTO {
    private Long id;
    private String url;
    private Long eventId;
    private String createdAt; // ISO-8601
}
