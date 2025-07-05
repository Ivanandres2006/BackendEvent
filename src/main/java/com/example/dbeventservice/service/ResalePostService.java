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
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        Optional<User> userOpt = userRepository.findById(userId);

        if (ticketOpt.isEmpty() || userOpt.isEmpty()) {
            throw new IllegalArgumentException("Ticket or user not found");
        }

        Ticket ticket = ticketOpt.get();

        // 🚫 Check if an unsold post already exists for this ticket
        boolean alreadyPosted = resalePostRepository.existsByTicketAndSoldFalse(ticket);
        if (alreadyPosted) {
            throw new IllegalArgumentException("This ticket is already listed for resale.");
        }

        ResalePost post = ResalePost.builder()
                .ticket(ticket)
                .seller(userOpt.get())
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
