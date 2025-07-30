package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.FCMToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FCMTokenRepository extends JpaRepository<FCMToken, Long> {
}
