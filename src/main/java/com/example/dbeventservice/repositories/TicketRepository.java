package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.TicketStatus;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = {"event", "ticketTier"})
    List<Ticket> findByOwner(User user);

    @EntityGraph(attributePaths = {"event", "ticketTier"})
    List<Ticket> findByOwnerAndStatus(User user, TicketStatus status);

    List<Ticket> findByEventId(Long eventId);

    int countByTicketTierId(Long tierId);

    @Query("SELECT t FROM Ticket t " +
            "JOIN FETCH t.event " +
            "JOIN FETCH t.ticketTier " +
            "WHERE t.owner = :owner AND t.status = :status")
    List<Ticket> findTicketsWithDetailsByOwnerAndStatus(@Param("owner") User owner,
                                                        @Param("status") TicketStatus status);

}
