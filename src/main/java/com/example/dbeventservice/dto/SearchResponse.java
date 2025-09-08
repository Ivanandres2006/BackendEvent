package com.example.dbeventservice.dto;

import java.util.List;

public class SearchResponse {
    private List<EventDTO> events;
    private List<String> cities;
    private List<UserLiteDTO> users;

    public SearchResponse() {}

    public SearchResponse(List<EventDTO> events, List<String> cities, List<UserLiteDTO> users) {
        this.events = events;
        this.cities = cities;
        this.users = users;
    }

    public List<EventDTO> getEvents() { return events; }
    public List<String> getCities() { return cities; }
    public List<UserLiteDTO> getUsers() { return users; }
    public void setEvents(List<EventDTO> events) { this.events = events; }
    public void setCities(List<String> cities) { this.cities = cities; }
    public void setUsers(List<UserLiteDTO> users) { this.users = users; }
}

