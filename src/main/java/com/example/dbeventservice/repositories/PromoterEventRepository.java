package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.PromoterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoterEventRepository extends JpaRepository<PromoterEvent, Long> {

    Optional<PromoterEvent> findByPromoterAndEvent(Promoter promoter, Event event);

    Optional<PromoterEvent> findByPromoter_User_IdAndEvent_Id(Long userId, Long eventId);

    List<PromoterEvent> findByEvent(Event event);

    List<PromoterEvent> findByPromoter(Promoter promoter);

    boolean existsByEvent_IdAndPromoter_User_Id(Long eventId, Long userId);
}
