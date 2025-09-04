package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.PromoterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoterEventRepository extends JpaRepository<PromoterEvent, Long> {
    Optional<PromoterEvent> findByPromoterAndEvent(Promoter promoter, Event event);
    List<PromoterEvent> findByEvent(Event event);
    List<PromoterEvent> findByPromoter_Id(Long promoterId);

    boolean existsByPromoter_User_IdAndEvent_Id(Long userId, Long eventId);

    @Query("""
        select coalesce(sum(pe.ticketsSold),0) from PromoterEvent pe
        where pe.promoter.id = :promoterId
    """)
    int totalTicketsSold(@Param("promoterId") Long promoterId);
}
