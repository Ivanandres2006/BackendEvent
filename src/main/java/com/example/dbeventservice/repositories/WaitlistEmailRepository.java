package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.WaitlistEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEmailRepository extends JpaRepository<WaitlistEmail, Long> {
}
