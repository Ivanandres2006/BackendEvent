package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.UserSummaryDTO;
import com.example.dbeventservice.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialController {
    private final FollowService followService;

    @PostMapping("/follow/{userId}")
    public ResponseEntity<?> follow(@AuthenticationPrincipal UserDetailsImpl me, @PathVariable Long userId) {
        followService.requestFollow(me.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/follow/{followerId}/accept")
    public ResponseEntity<?> accept(@AuthenticationPrincipal UserDetailsImpl me, @PathVariable Long followerId) {
        followService.accept(me.getId(), followerId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<?> unfollow(@AuthenticationPrincipal UserDetailsImpl me, @PathVariable Long userId) {
        followService.unfollow(me.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/followers/{userId}")
    public List<UserSummaryDTO> followers(@PathVariable Long userId) {
        return followService.followers(userId).stream().map(UserSummaryDTO::from).toList();
    }

    @GetMapping("/following/{userId}")
    public List<UserSummaryDTO> following(@PathVariable Long userId) {
        return followService.following(userId).stream().map(UserSummaryDTO::from).toList();
    }

    @GetMapping("/friends/{userId}")
    public List<UserSummaryDTO> friends(@PathVariable Long userId) {
        return followService.friends(userId).stream().map(UserSummaryDTO::from).toList();
    }
}
