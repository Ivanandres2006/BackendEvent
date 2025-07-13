package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

}
