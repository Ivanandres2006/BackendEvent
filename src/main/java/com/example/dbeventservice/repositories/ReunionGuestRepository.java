package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ReunionGuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReunionGuestRepository extends JpaRepository<ReunionGuest, Long> {

    List<ReunionGuest> findByEventId(Long eventId);

    Optional<ReunionGuest> findByUsernameIgnoreCaseAndEventId(String username, Long eventId);
}
