package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoterEventRepository extends JpaRepository<PromoterEvent, Long> {
    List<PromoterEvent> findByPromoter(Promoter promoter);
    List<PromoterEvent> findByEvent(Event event);
    Optional<PromoterEvent> findByPromoterAndEvent(Promoter promoter, Event event);

    Optional<PromoterEvent> findByPromoterIdAndEventId(Long promoterId, Long eventId);

    List<PromoterEvent> findByPromoter_Id(Long promoterId);

    boolean existsByPromoter_User_IdAndEvent_Id(Long userId, Long eventId);

    @Query("""
        select coalesce(sum(pe.ticketsSold),0) from PromoterEvent pe
        where pe.promoter.id = :promoterId
    """)
    int totalTicketsSold(@Param("promoterId") Long promoterId);
}
