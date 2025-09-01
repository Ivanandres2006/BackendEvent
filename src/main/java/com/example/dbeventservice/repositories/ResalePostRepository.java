package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResalePostRepository extends JpaRepository<ResalePost, Long> {
    List<ResalePost> findBySoldFalse();
    List<ResalePost> findBySeller(User seller);


    boolean existsByTicketAndSoldFalse(Ticket ticket);

    @Query("""
           select rp 
           from ResalePost rp
           left join fetch rp.seller
           left join fetch rp.ticket
           """)
    List<ResalePost> findAllWithSellerAndTicket();

    List<ResalePost> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    // Lookup
    Optional<ResalePost> findById(Long id);

    // Market feed: available = not sold, not paused, ticket's event in future (filter in service/controller if event join is not present)
    List<ResalePost> findBySoldFalseAndPausedFalseOrderByCreatedAtDesc();

    List<ResalePost> findAvailableWithFutureEvent(@Param("now") LocalDateTime now);

}
