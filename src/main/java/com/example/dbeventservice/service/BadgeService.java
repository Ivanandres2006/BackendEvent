package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.BadgeDTO;
import com.example.dbeventservice.model.Badge;
import com.example.dbeventservice.model.UserBadge;
import com.example.dbeventservice.repositories.BadgeRepository;
import com.example.dbeventservice.repositories.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BadgeService {
    private final BadgeRepository badges;
    private final UserBadgeRepository userBadges;

    public List<BadgeDTO> badgesForUser(Long userId) {
        var fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return userBadges.findByUserIdOrderByAwardedAtDesc(userId).stream()
                .map(ub -> new BadgeDTO(
                        ub.getBadge().getKey(),
                        ub.getBadge().getName(),
                        ub.getBadge().getIconUrl(),
                        ub.getBadge().getDescription(),
                        ub.getAwardedAt() != null ? fmt.format(ub.getAwardedAt()) : null
                ))
                .toList();
    }

    public void grant(Long userId, String badgeKey, String reason) {
        var badge = badges.findById(badgeKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BADGE_NOT_FOUND"));
        var existing = userBadges.findByUserIdAndBadge_Key(userId, badgeKey);
        if (existing.isPresent()) return; // idempotent
        var ub = new UserBadge();
        ub.setUserId(userId);
        ub.setBadge(badge);
        ub.setReason(reason);
        userBadges.save(ub);
    }

    public void revoke(Long userId, String badgeKey) {
        userBadges.deleteByUserIdAndBadge_Key(userId, badgeKey);
    }

    public void setIcon(String badgeKey, String iconUrl) {
        var b = badges.findById(badgeKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BADGE_NOT_FOUND"));
        b.setIconUrl(iconUrl);
        badges.save(b);
    }
}
