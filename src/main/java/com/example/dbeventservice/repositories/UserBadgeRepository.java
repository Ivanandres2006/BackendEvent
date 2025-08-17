package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserIdOrderByAwardedAtDesc(Long userId);
    Optional<UserBadge> findByUserIdAndBadge_Key(Long userId, String badgeKey);
    void deleteByUserIdAndBadge_Key(Long userId, String badgeKey);
}
