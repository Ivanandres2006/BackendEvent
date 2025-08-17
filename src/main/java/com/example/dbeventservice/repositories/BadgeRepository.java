package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, String> { }
