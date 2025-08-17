package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.BadgeDTO;
import com.example.dbeventservice.dto.GrantBadgeRequest;
import com.example.dbeventservice.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {
    private final BadgeService svc;

    @GetMapping("/me")
    public List<BadgeDTO> myBadges(@AuthenticationPrincipal UserDetailsImpl me) {
        return svc.badgesForUser(me.getId());
    }

    @GetMapping("/{userId}")
    public List<BadgeDTO> userBadges(@PathVariable Long userId) {
        return svc.badgesForUser(userId);
    }

    @PostMapping("/{userId}/{badgeKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> grantBadge(
            @PathVariable Long userId,
            @PathVariable String badgeKey,
            @RequestBody(required = false) GrantBadgeRequest body
    ) {
        svc.grant(userId, badgeKey, body != null ? body.getReason() : null);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{userId}/{badgeKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> revokeBadge(
            @PathVariable Long userId,
            @PathVariable String badgeKey
    ) {
        svc.revoke(userId, badgeKey);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
