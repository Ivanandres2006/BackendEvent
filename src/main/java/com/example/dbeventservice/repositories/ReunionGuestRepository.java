package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ReunionGuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReunionGuestRepository extends JpaRepository<ReunionGuest, Long> {

    List<ReunionGuest> findByEventId(Long eventId);

}
