package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.WaitlistEntry;
import com.example.dbeventservice.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByEvent(Event event);
    Optional<WaitlistEntry> findByEventIdAndUsername(Long eventId, String username);
}
