package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByCreatedById(Long userId);

    List<Event> findByCreatedBy(User user);

    List<Event> findByLocationIgnoreCaseContaining(String location);

    Optional<Event> findById(Long id);

    List<Event> findByTitleContainingIgnoreCase(String keyword);
}
