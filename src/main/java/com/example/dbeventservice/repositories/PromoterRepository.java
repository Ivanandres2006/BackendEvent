package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Promoter;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoterRepository extends JpaRepository<Promoter, Long> {
    Optional<Promoter> findByUser(User user);
    Optional<Promoter> findByReferralCode(String code);
}
