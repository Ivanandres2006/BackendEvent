// src/main/java/com/example/dbeventservice/repositories/DiscountCodeRepository.java
package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByEventIdAndCodeIgnoreCase(Long eventId, String code);
    List<DiscountCode> findByEventIdOrderByIdDesc(Long eventId);
}
