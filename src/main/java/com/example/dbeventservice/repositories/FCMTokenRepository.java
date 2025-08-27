package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.FCMToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FCMTokenRepository extends JpaRepository<FCMToken, Long> {

    Optional<FCMToken> findByToken(String token);

    List<FCMToken> findAllByUsername(String username);

    void deleteByToken(String token);

    List<FCMToken> findAllByUsernameIn(Collection<String> usernames);

}
