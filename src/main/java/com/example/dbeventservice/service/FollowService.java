package com.example.dbeventservice.service;

import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.FollowRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service @RequiredArgsConstructor
public class FollowService {
    private final FollowRepository followRepo;
    private final UserRepository userRepo;

    @Transactional
    public void requestFollow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) throw new IllegalArgumentException("Cannot follow yourself");
        User follower = userRepo.findById(followerId).orElseThrow();
        User followee = userRepo.findById(followeeId).orElseThrow();

        var existing = followRepo.findByFollowerAndFollowee(follower, followee).orElse(null);
        if (existing == null) {
            existing = Follow.builder()
                    .follower(follower)
                    .followee(followee)
                    .status(FollowStatus.PENDING)
                    .build();
        }

        // If the reverse exists, auto-accept mutual
        Follow finalExisting = existing;
        followRepo.findByFollowerAndFollowee(followee, follower).ifPresent(other -> {
            if (other.getStatus() == FollowStatus.PENDING) other.setStatus(FollowStatus.ACCEPTED);
            if (finalExisting.getStatus() == FollowStatus.PENDING) finalExisting.setStatus(FollowStatus.ACCEPTED);
        });

        followRepo.save(existing);
    }

    @Transactional
    public void accept(Long currentUserId, Long followerId) {
        User me = userRepo.findById(currentUserId).orElseThrow();
        User follower = userRepo.findById(followerId).orElseThrow();
        Follow f = followRepo.findByFollowerAndFollowee(follower, me).orElseThrow();
        f.setStatus(FollowStatus.ACCEPTED);
        followRepo.save(f);
    }

    @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        User me = userRepo.findById(currentUserId).orElseThrow();
        User target = userRepo.findById(targetUserId).orElseThrow();
        followRepo.findByFollowerAndFollowee(me, target).ifPresent(followRepo::delete);
        // optional: also drop reverse ACCEPTED to “unfriend”
        followRepo.findByFollowerAndFollowee(target, me).ifPresent(f -> {
            if (f.getStatus() == FollowStatus.ACCEPTED) followRepo.delete(f);
        });
    }

    public List<User> followers(Long userId) {
        return followRepo.findAcceptedFollowers(userRepo.findById(userId).orElseThrow());
    }
    public List<User> following(Long userId) {
        return followRepo.findAcceptedFollowing(userRepo.findById(userId).orElseThrow());
    }
    public List<User> friends(Long userId) {
        return followRepo.findFriends(userRepo.findById(userId).orElseThrow());
    }

    // add under existing methods

    @Transactional
    public void reject(Long currentUserId, Long followerId) {
        User me = userRepo.findById(currentUserId).orElseThrow();
        User follower = userRepo.findById(followerId).orElseThrow();
        followRepo.findByFollowerAndFollowee(follower, me).ifPresent(followRepo::delete);
    }

    public record Relationship(String outgoing, String incoming) {}
    public Relationship relationship(Long meId, Long otherId) {
        User me = userRepo.findById(meId).orElseThrow();
        User other = userRepo.findById(otherId).orElseThrow();

        String out = followRepo.findByFollowerAndFollowee(me, other)
                .map(f -> f.getStatus().name()).orElse("NONE");  // NONE/PENDING/ACCEPTED
        String in  = followRepo.findByFollowerAndFollowee(other, me)
                .map(f -> f.getStatus().name()).orElse("NONE");
        return new Relationship(out, in);
    }

    public List<Follow> pendingRequests(Long meId) {
        return followRepo.findPendingRequests(userRepo.findById(meId).orElseThrow());
    }

}
