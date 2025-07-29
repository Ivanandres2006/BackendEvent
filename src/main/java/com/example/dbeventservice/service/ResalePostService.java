package com.example.dbeventservice.service;

import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.model.Ticket;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.ResalePostRepository;
import com.example.dbeventservice.repositories.TicketRepository;
import com.example.dbeventservice.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ResalePostService {

    @Autowired
    private ResalePostRepository resalePostRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    public ResalePost createResalePost(Long ticketId, double price, String title, String description, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (ticket.getOwner() == null || !ticket.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("You don't own this ticket.");
        }
        if (!ticket.isPaid()) {
            throw new IllegalArgumentException("Only paid tickets can be listed.");
        }
        if (ticket.isUsed()) {
            throw new IllegalArgumentException("Used tickets cannot be listed.");
        }
        if (resalePostRepository.existsByTicketAndSoldFalse(ticket)) {
            throw new IllegalArgumentException("This ticket is already listed for resale.");
        }

        ResalePost post = ResalePost.builder()
                .ticket(ticket)
                .seller(user)
                .price(price)
                .title(title)
                .description(description)
                .sold(false)
                .build();

        return resalePostRepository.save(post);
    }



    public List<ResalePost> getAllAvailablePosts() {
        return resalePostRepository.findBySoldFalse();
    }

    public List<ResalePost> getUserPosts(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return resalePostRepository.findBySeller(user);
    }

    public Optional<ResalePost> getPostById(Long postId) {
        return resalePostRepository.findById(postId);
    }

    public void markAsSold(Long postId) {
        resalePostRepository.findById(postId).ifPresent(post -> {
            post.setSold(true);
            resalePostRepository.save(post);
        });
    }
}
