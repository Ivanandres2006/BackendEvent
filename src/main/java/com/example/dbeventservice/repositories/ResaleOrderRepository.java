package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.ResaleOrder;
import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.Enum.ResaleOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResaleOrderRepository extends JpaRepository<ResaleOrder, Long> {
    Optional<ResaleOrder> findTopByResalePostOrderByCreatedAtDesc(ResalePost resalePost);

    // NEW: all pending manual orders for a seller
    List<ResaleOrder> findByResalePost_Seller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, ResaleOrderStatus status);

    // NEW: filter pending by post
    List<ResaleOrder> findByResalePost_IdAndResalePost_Seller_IdAndStatusOrderByCreatedAtDesc(
            Long resalePostId, Long sellerId, ResaleOrderStatus status);

    // NEW: count helpers
    long countByResalePostAndStatus(ResalePost resalePost, ResaleOrderStatus status);

    List<ResaleOrder> findByResalePostAndStatus(ResalePost resalePost, ResaleOrderStatus status);

}
