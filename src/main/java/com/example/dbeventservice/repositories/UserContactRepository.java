package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Enum.ContactType;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    List<UserContact> findByOwnerAndType(User owner, ContactType type);
}
