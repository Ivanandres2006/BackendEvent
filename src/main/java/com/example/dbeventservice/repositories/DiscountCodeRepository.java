// src/main/java/com/example/dbeventservice/repositories/DiscountCodeRepository.java
package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByEventIdAndCodeIgnoreCase(Long eventId, String code);
    List<DiscountCode> findByEventIdOrderByIdDesc(Long eventId);

    @Query("""
        select d from DiscountCode d
        where upper(d.code) = upper(?1)
          and d.eventId = ?2
          and (d.active is null or d.active = true)
    """)
    Optional<DiscountCode> findActiveByCodeAndEvent(String code, Long eventId);
}
