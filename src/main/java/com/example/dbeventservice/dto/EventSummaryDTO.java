package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Event;
import lombok.Getter;

@Getter
public class EventSummaryDTO {
    private final Long id;
    private final String title;
    private final String imageUrl;
    private final String location;

    public EventSummaryDTO(Long id, String title, String imageUrl, String location) {
        this.id = id; this.title = title; this.imageUrl = imageUrl; this.location = location;
    }
    public static EventSummaryDTO from(Event e) {
        return new EventSummaryDTO(e.getId(), e.getTitle(), e.getImageUrl(), e.getLocation());
    }
}
