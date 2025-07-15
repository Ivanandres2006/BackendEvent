package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ReunionGuest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReunionGuestRepository extends JpaRepository<ReunionGuest, Long> {
}
