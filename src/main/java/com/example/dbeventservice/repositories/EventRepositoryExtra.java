package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Event;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepositoryExtra {
    @Query("""
        SELECT e FROM Event e
        WHERE e.dateTime >= :now AND (
              LOWER(e.title)     LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.location)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        )
        ORDER BY e.dateTime ASC
    """)
    List<Event> searchEvents(@Param("q") String q, @Param("now") LocalDateTime now);

    @Query("""
        SELECT DISTINCT e.location FROM Event e
        WHERE e.dateTime >= :now
          AND LOWER(e.location) LIKE LOWER(CONCAT('%', :q, '%'))
          AND e.location IS NOT NULL AND e.location <> ''
        ORDER BY e.location ASC
    """)
    List<String> searchCities(@Param("q") String q, @Param("now") LocalDateTime now);
}
