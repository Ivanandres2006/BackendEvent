package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepositoryExtra {
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.username)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(u.lastName,  '')) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY u.username ASC
    """)
    List<User> searchUsers(@Param("q") String q);
}
