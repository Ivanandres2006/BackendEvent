package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.ResalePostDTO;
import com.example.dbeventservice.model.ResalePost;
import com.example.dbeventservice.service.ResalePostService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resale")
public class ResalePostController {

    @Autowired
    private ResalePostService resalePostService;

    @PostMapping("/create")
    public ResponseEntity<?> createResalePost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody CreateResalePostRequest request) {

        try {
            ResalePost post = resalePostService.createResalePost(
                    request.ticketId,
                    request.price,
                    request.title,
                    request.description,
                    userDetails.getId()
            );
            return ResponseEntity.ok(new ResalePostDTO(post));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<ResalePostDTO>> getAvailablePosts() {
        List<ResalePostDTO> dtos = resalePostService.getAllAvailablePosts().stream()
                .map(ResalePostDTO::new)
                .toList();
        return ResponseEntity.ok(dtos);
    }


    @GetMapping("/my")
    public ResponseEntity<List<ResalePost>> getUserPosts(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(resalePostService.getUserPosts(userDetails.getId()));
    }

    @PostMapping("/{postId}/mark-sold")
    public ResponseEntity<?> markPostAsSold(@PathVariable Long postId) {
        resalePostService.markAsSold(postId);
        return ResponseEntity.ok("Marked as sold");
    }

    // DTO
    @Getter
    @Setter
    public static class CreateResalePostRequest {
        @NotNull
        public Long ticketId;

        @NotNull
        public Double price;

        @NotBlank
        public String title;

        public String description;
    }
}
