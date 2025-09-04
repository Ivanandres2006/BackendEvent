package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.PromoterEvent;
import com.example.dbeventservice.model.PromoterSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoterSaleRepository extends JpaRepository<PromoterSale, Long> {
    List<PromoterSale> findByPromoterEvent(PromoterEvent promoterEvent);
    long countByPromoterEvent(PromoterEvent promoterEvent);
}

