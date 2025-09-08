package com.example.dbeventservice.dto;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.ReunionGuest;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EventDTO {
    private Long id;
    private String title;
    private String location;
    private String dateTime;       // ISO string for iOS
    private String type;
    private UserMiniDTO createdBy;
    private String createdByEmail;
    private String description;
    private List<ReunionGuestDTO> reunionGuests;

    public EventDTO() {}

    public static EventDTO fromEntity(Event e) {
        EventDTO dto = new EventDTO();
        dto.id = e.getId();
        dto.title = e.getTitle();
        dto.location = e.getLocation();
        // Convert LocalDateTime to ISO-8601 "yyyy-MM-dd'T'HH:mm:ss"
        if (e.getDateTime() != null) {
            dto.dateTime = e.getDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        dto.type = e.getType();
        if (e.getCreatedBy() != null) {
            dto.createdBy = UserMiniDTO.fromEntity(e.getCreatedBy());
        }
        dto.createdByEmail = e.getCreatorEmail();
        dto.description = e.getDescription();

        if (e.getReunionGuests() != null) {
            dto.reunionGuests = e.getReunionGuests().stream()
                    .filter(Objects::nonNull)
                    .map(ReunionGuestDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    // getters & setters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getDateTime() { return dateTime; }
    public String getType() { return type; }
    public UserMiniDTO getCreatedBy() { return createdBy; }
    public String getCreatedByEmail() { return createdByEmail; }
    public String getDescription() { return description; }
    public List<ReunionGuestDTO> getReunionGuests() { return reunionGuests; }

    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setLocation(String location) { this.location = location; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
    public void setType(String type) { this.type = type; }
    public void setCreatedBy(UserMiniDTO createdBy) { this.createdBy = createdBy; }
    public void setCreatedByEmail(String createdByEmail) { this.createdByEmail = createdByEmail; }
    public void setDescription(String description) { this.description = description; }
    public void setReunionGuests(List<ReunionGuestDTO> reunionGuests) { this.reunionGuests = reunionGuests; }
}
