package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.FollowRequestDTO;
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

    @DeleteMapping("/follow/{followerId}/reject")
    public ResponseEntity<?> reject(@AuthenticationPrincipal UserDetailsImpl me,
                                    @PathVariable Long followerId) {
        followService.reject(me.getId(), followerId);
        return ResponseEntity.ok().build();
    }

    // RELATIONSHIP (tells if outgoing is PENDING/ACCEPTED/NONE)
    record RelationshipDTO(String outgoing, String incoming) {}
    @GetMapping("/relationship/{otherId}")
    public RelationshipDTO relationship(@AuthenticationPrincipal UserDetailsImpl me,
                                        @PathVariable Long otherId) {
        var r = followService.relationship(me.getId(), otherId);
        return new RelationshipDTO(r.outgoing(), r.incoming());
    }


    @GetMapping("/requests")
    public List<FollowRequestDTO> requests(@AuthenticationPrincipal UserDetailsImpl me,
                                           @RequestParam(defaultValue = "50") int limit) {
        return followService.pendingRequests(me.getId())
                .stream()
                .limit(Math.max(1, Math.min(100, limit)))
                .map(f -> {
                    var u = f.getFollower();
                    String dn = ((u.getFirstName()==null?"":u.getFirstName()) +
                            (u.getLastName()==null?"":" "+u.getLastName())).trim();
                    if (dn.isBlank()) dn = u.getUsername();
                    return new FollowRequestDTO(u.getId(), u.getUsername(), dn, f.getCreatedAt().toString());
                })
                .toList();
    }


    // (Optional) quick badge for a red-dot in your app
    public record CountDTO(int count) {}
    @GetMapping("/requests/count")
    public CountDTO requestsCount(@AuthenticationPrincipal UserDetailsImpl me) {
        return new CountDTO(followService.pendingCount(me.getId()));
    }

}
