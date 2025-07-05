package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {
    List<TicketTier> findByEventId(Long eventId);

    List<TicketTier> findByEventIdOrderByTierOrderAsc(Long eventId);
}
