package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationCode(String code);
    Optional<User> findByExternalProviderAndExternalId(String provider, String externalId);
    Boolean existsByEmail(String email);
    Boolean existsByPhone(String phone);

    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.canCreateEvents = :status WHERE u.id = :userId")
    void updateEventCreationPermission(@Param("userId") Long userId, @Param("status") boolean status);

    // UserRepository.java
    @Query("SELECT u.username FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT(:query, '%'))")
    List<String> findUsernamesStartingWith(@Param("query") String query);

}
