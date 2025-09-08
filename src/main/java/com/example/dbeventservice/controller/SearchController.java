package com.example.dbeventservice.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.dbeventservice.dto.EventDTO;
import com.example.dbeventservice.dto.SearchResponse;
import com.example.dbeventservice.dto.UserLiteDTO;
import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.EventRepository;
import com.example.dbeventservice.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public SearchController(EventRepository eventRepository, UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // Unified search: /search?q=...
    @GetMapping
    public ResponseEntity<SearchResponse> search(@RequestParam("q") String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return ResponseEntity.ok(new SearchResponse(List.of(), List.of(), List.of()));
        }

        LocalDateTime now = LocalDateTime.now();

        List<Event> events = eventRepository.searchEvents(query, now);
        List<EventDTO> eventDTOs = events.stream().map(EventDTO::fromEntity).collect(Collectors.toList());

        List<String> cities = eventRepository.searchCities(query, now);

        List<User> users = userRepository.searchUsers(query);
        List<UserLiteDTO> userDTOs = users.stream().map(UserLiteDTO::fromEntity).collect(Collectors.toList());

        return ResponseEntity.ok(new SearchResponse(eventDTOs, cities, userDTOs));
    }

    // Optional compatibility endpoints if you want separate calls:
    @GetMapping("/users")
    public ResponseEntity<List<UserLiteDTO>> searchUsers(@RequestParam("q") String q) {
        List<UserLiteDTO> users = userRepository.searchUsers(q).stream()
                .map(UserLiteDTO::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventDTO>> searchEvents(@RequestParam("q") String q) {
        List<EventDTO> evs = eventRepository.searchEvents(q, LocalDateTime.now()).stream()
                .map(EventDTO::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(evs);
    }

    @GetMapping("/cities")
    public ResponseEntity<List<String>> searchCities(@RequestParam("q") String q) {
        return ResponseEntity.ok(eventRepository.searchCities(q, LocalDateTime.now()));
    }
}

