package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.FCMToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FCMTokenRepository extends JpaRepository<FCMToken, Long> {

    Optional<FCMToken> findByToken(String token);

    void deleteByToken(String token);
}
