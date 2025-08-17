package com.example.dbeventservice.controller;

import com.example.dbeventservice.auth.jwt.UserDetailsImpl;
import com.example.dbeventservice.dto.FollowRequestDTO;
import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.repositories.FollowRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialExtraController {
    private final FollowRepository follows;
    private final UserRepository users;

    @GetMapping("/requests")
    public List<FollowRequestDTO> incoming(
            @AuthenticationPrincipal UserDetailsImpl me,
            @RequestParam(defaultValue = "50") int limit
    ) {
        var page = PageRequest.of(0, Math.min(100, Math.max(1, limit)));
        // I'm the followee; PENDING means someone requested to follow me
        List<Follow> rows = follows.findByFollowee_IdAndStatusOrderByIdDesc(me.getId(), FollowStatus.PENDING, page);

        List<Long> followerIds = rows.stream()
                .map(r -> r.getFollower().getId())
                .toList();

        Map<Long, User> userMap = users.findAllById(followerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        return rows.stream().map(r -> {
            User u = userMap.get(r.getFollower().getId());
            String username = (u != null && u.getUsername() != null) ? u.getUsername() : ("user" + r.getFollower().getId());
            String display = (u != null)
                    ? ((u.getFirstName() != null ? u.getFirstName() : "") + " " +
                    (u.getLastName() != null ? u.getLastName() : "")).trim()
                    : null;
            String ts = r.getCreatedAt() != null ? fmt.format(r.getCreatedAt()) : null;
            return new FollowRequestDTO(r.getFollower().getId(), username, display, ts);
        }).toList();
    }

    /** Ignore / reject a pending follow request (follower -> me). */
    @DeleteMapping("/follow/{followerId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @AuthenticationPrincipal UserDetailsImpl me,
            @PathVariable Long followerId
    ) {
        follows.deleteByFollower_IdAndFollowee_IdAndStatus(followerId, me.getId(), FollowStatus.PENDING);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
