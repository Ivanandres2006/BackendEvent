package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Event;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.Enum.TicketStatus;
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

    @Query("SELECT t FROM Ticket t " +
            "JOIN FETCH t.ticketTier " +
            "JOIN FETCH t.event e " +
            "JOIN FETCH t.owner " +
            "WHERE e.id = :eventId")
    List<Ticket> findTicketsWithDetailsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT DISTINCT t.owner FROM Ticket t WHERE t.event.id = :eventId AND t.status = :status")
    List<User> findDistinctOwnersByEventIdAndStatus(@Param("eventId") Long eventId,
                                                    @Param("status") TicketStatus status);

    @Query("SELECT DISTINCT t.event FROM Ticket t WHERE t.owner.id = :userId AND t.status = :status")
    List<Event> findDistinctEventsByOwnerAndStatus(@Param("userId") Long userId,
                                                   @Param("status") TicketStatus status);

    /**
     * Returns true if the user owns a PAID, non-refunded ticket for the given event.
     * Adjust the field names if your schema differs:
     *  - owner -> buyer
     *  - paid/status/refunded flags
     */
    @Query("""
           select (count(t) > 0)
           from Ticket t
           where t.owner.id = :userId
             and t.event.id = :eventId
             and (
                  (t.paid = true)
                  or (t.status = 'PAID')
             )
           """)
    boolean userHasPaidTicketForEvent(@Param("userId") Long userId,
                                      @Param("eventId") Long eventId);


}
