package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Enum.ContactType;
import com.example.dbeventservice.model.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    List<UserIdentity> findByTypeAndValueHashIn(ContactType type, List<String> hashes);
}
