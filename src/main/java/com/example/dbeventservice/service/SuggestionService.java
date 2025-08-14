package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.SuggestionDTO;
import com.example.dbeventservice.model.Enum.ContactType;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.FollowRepository;
import com.example.dbeventservice.repositories.UserIdentityRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SuggestionService {
    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final UserIdentityRepository identityRepo;

    public List<SuggestionDTO> suggestions(Long currentUserId, List<String> contactEmailHashes, List<String> contactPhoneHashes, int limit) {
        User me = userRepo.findById(currentUserId).orElseThrow();

        // Already followed or self
        Set<Long> exclude = new HashSet<>();
        exclude.add(me.getId());
        followRepo.findAcceptedFollowing(me).forEach(u -> exclude.add(u.getId()));
        followRepo.findByFollowerAndStatus(me, com.example.dbeventservice.model.Enum.FollowStatus.PENDING)
                .forEach(f -> exclude.add(f.getFollowee().getId()));

        // 1) Contacts-based matches (users who published identities matching your contacts)
        List<SuggestionDTO> contactBased = new ArrayList<>();
        if (!contactEmailHashes.isEmpty()) {
            identityRepo.findByTypeAndValueHashIn(ContactType.EMAIL, contactEmailHashes).forEach(id -> {
                User u = id.getUser();
                if (!exclude.contains(u.getId()) && !u.isHideProfile()) {
                    contactBased.add(SuggestionDTO.fromContacts(u));
                    exclude.add(u.getId());
                }
            });
        }
        if (!contactPhoneHashes.isEmpty()) {
            identityRepo.findByTypeAndValueHashIn(ContactType.PHONE, contactPhoneHashes).forEach(id -> {
                User u = id.getUser();
                if (!exclude.contains(u.getId()) && !u.isHideProfile()) {
                    contactBased.add(SuggestionDTO.fromContacts(u));
                    exclude.add(u.getId());
                }
            });
        }

        // 2) Mutual-follow graph: people followed by people you follow
        Map<User, Integer> mutualCounts = new HashMap<>();
        followRepo.findAcceptedFollowing(me).forEach(f1 -> {
            followRepo.findAcceptedFollowing(f1).forEach(candidate -> {
                if (!exclude.contains(candidate.getId()) && !candidate.isHideProfile()) {
                    mutualCounts.merge(candidate, 1, Integer::sum);
                }
            });
        });

        List<SuggestionDTO> mutuals = mutualCounts.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.max(0, limit - contactBased.size()))
                .map(e -> SuggestionDTO.fromMutuals(e.getKey(), e.getValue()))
                .toList();

        // Merge with a simple priority: contacts first, then mutuals
        return new ArrayList<>() {{ addAll(contactBased.stream().limit(limit).toList()); addAll(mutuals); }};
    }
}
