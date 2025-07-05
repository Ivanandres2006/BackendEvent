package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoterEventRepository extends JpaRepository<PromoterEvent, Long> {
    List<PromoterEvent> findByPromoter(Promoter promoter);
    List<PromoterEvent> findByEvent(Event event);
    Optional<PromoterEvent> findByPromoterAndEvent(Promoter promoter, Event event);

    Optional<PromoterEvent> findByPromoterIdAndEventId(Long promoterId, Long eventId);

}
