// dbeventservice/controller/ResaleManageController.java
package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ResaleAnalyticsDTO;
import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.repositories.ResalePostRepository;
import lombok.Getter; import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/resale")
public class ResaleManageController {

    private final ResalePostRepository postRepo;

    public ResaleManageController(ResalePostRepository postRepo) {
        this.postRepo = postRepo;
    }

    // ---------- PRICE ----------
    @Getter @Setter
    public static class PriceReq { private Double price; }

    @PatchMapping("/{postId}/price")
    public ResponseEntity<?> updatePrice(@AuthenticationPrincipal UserDetailsImpl user,
                                         @PathVariable Long postId,
                                         @RequestBody PriceReq req) {
        var post = postRepo.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!post.getSeller().getId().equals(user.getId())) return ResponseEntity.badRequest().body(err("Not your post"));
        if (post.isSold()) return ResponseEntity.badRequest().body(err("Already sold"));
        if (req.getPrice() == null || req.getPrice() <= 0) return ResponseEntity.badRequest().body(err("Invalid price"));
        post.setPrice(req.getPrice());
        postRepo.save(post);
        return ResponseEntity.ok(ok("priceUpdated"));
    }

    // ---------- PAUSE / RESUME ----------
    @PostMapping("/{postId}/pause")
    public ResponseEntity<?> pause(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable Long postId) {
        var post = postRepo.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!post.getSeller().getId().equals(user.getId())) return ResponseEntity.badRequest().body(err("Not your post"));
        if (post.isSold()) return ResponseEntity.badRequest().body(err("Already sold"));
        post.setPaused(true);
        postRepo.save(post);
        return ResponseEntity.ok(ok("paused"));
    }

    @PostMapping("/{postId}/resume")
    public ResponseEntity<?> resume(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable Long postId) {
        var post = postRepo.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!post.getSeller().getId().equals(user.getId())) return ResponseEntity.badRequest().body(err("Not your post"));
        post.setPaused(false);
        postRepo.save(post);
        return ResponseEntity.ok(ok("resumed"));
    }

    // ---------- TRACK ----------
    // These can be anonymous; if you prefer, require auth.
    @PostMapping("/{postId}/track/view")
    public ResponseEntity<?> trackView(@PathVariable Long postId) {
        postRepo.findById(postId).ifPresent(p -> { p.incViews(); postRepo.save(p); });
        return ResponseEntity.ok(ok("viewTracked"));
    }

    @PostMapping("/{postId}/track/chat-open")
    public ResponseEntity<?> trackChatOpen(@PathVariable Long postId) {
        postRepo.findById(postId).ifPresent(p -> { p.incChatOpens(); postRepo.save(p); });
        return ResponseEntity.ok(ok("chatOpenTracked"));
    }

    // ---------- ANALYTICS ----------
    @GetMapping("/my/analytics")
    public ResponseEntity<?> myAnalytics(@AuthenticationPrincipal UserDetailsImpl user) {
        var list = postRepo.findBySeller_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(ResaleAnalyticsDTO::from).toList();
        return ResponseEntity.ok(list);
    }

    // ---------- DEEP LINK ----------
    @Value("${app.resale.deepLinkBase:https://wkndevent.com/r}")
    private String deepLinkBase;

    @GetMapping("/deeplink/{postId}")
    public ResponseEntity<?> deepLink(@PathVariable Long postId) {
        // Basic sanity check
        if (postRepo.findById(postId).isEmpty()) return ResponseEntity.badRequest().body(err("Not found"));
        return ResponseEntity.ok(java.util.Map.of("url", deepLinkBase + "/" + postId));
    }

    // helpers
    private static java.util.Map<String,String> err(String m){ return java.util.Map.of("error", m); }
    private static java.util.Map<String,String> ok(String s){ return java.util.Map.of("status", s); }
}
