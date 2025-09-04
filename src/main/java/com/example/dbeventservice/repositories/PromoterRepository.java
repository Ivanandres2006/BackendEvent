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
    Optional<Promoter> findByReferralCode(String code);

    boolean existsByEvent_IdAndUser_Id(Long eventId, Long userId);
    // PromoterRepository.java
    @Query("SELECT pe.promoter FROM PromoterEvent pe WHERE pe.event.id = :eventId")
    List<Promoter> findAllByEvent_Id(@Param("eventId") Long eventId);

    List<Promoter> findAllByUserId(Long userId);

    boolean existsByUser(User user);
    boolean existsByReferralCode(String referralCode);



}
