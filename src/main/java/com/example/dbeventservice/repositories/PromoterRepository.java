package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromoterRepository extends JpaRepository<Promoter, Long> {
    Optional<Promoter> findByUser(User user);
    Optional<Promoter> findByUser_Id(Long userId);

    Optional<Promoter> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);

    // Optional: eager-load events & event for a single call
    @Query("""
        select p from Promoter p
        left join fetch p.events pe
        left join fetch pe.event
        where p.user = :user
    """)
    Optional<Promoter> findWithEventsByUser(@Param("user") User user);



}
