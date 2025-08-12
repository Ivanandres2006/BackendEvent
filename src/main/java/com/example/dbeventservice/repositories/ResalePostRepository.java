package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResalePostRepository extends JpaRepository<ResalePost, Long> {
    List<ResalePost> findBySoldFalse();
    List<ResalePost> findBySeller(User seller);

    boolean existsByTicketAndSoldFalse(Ticket ticket);

    @EntityGraph(attributePaths = {"seller", "ticket"})
    List<ResalePost> findAllWithSellerAndTicket();
}
