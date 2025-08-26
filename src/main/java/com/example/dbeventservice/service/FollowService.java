package com.example.dbeventservice.service;

import com.example.dbeventservice.dto.SocialCountsDTO;
import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.FollowRepository;
import com.example.dbeventservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepo;
    private final UserRepository userRepo;

    // ---- Mutations ----

    @Transactional
    public void requestFollow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        User follower = userRepo.findById(followerId).orElseThrow();
        User followee = userRepo.findById(followeeId).orElseThrow();

        // my (follower -> followee) relation
        Follow mine = followRepo.findByFollowerAndFollowee(follower, followee).orElse(null);
        if (mine == null) {
            mine = new Follow();
            mine.setFollower(follower);
            mine.setFollowee(followee);
            mine.setStatus(FollowStatus.PENDING);
        }

        // reverse relation (followee -> follower)
        var reverseOpt = followRepo.findByFollowerAndFollowee(followee, follower);
        if (reverseOpt.isPresent()) {
            var reverse = reverseOpt.get();
            // if reverse exists (PENDING or ACCEPTED), make both ACCEPTED (mutual)
            if (reverse.getStatus() == FollowStatus.PENDING) {
                reverse.setStatus(FollowStatus.ACCEPTED);
                followRepo.save(reverse);
            }
            mine.setStatus(FollowStatus.ACCEPTED);
        }

        followRepo.save(mine);
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
    public void reject(Long currentUserId, Long followerId) {
        User me = userRepo.findById(currentUserId).orElseThrow();
        User follower = userRepo.findById(followerId).orElseThrow();
        followRepo.findByFollowerAndFollowee(follower, me).ifPresent(followRepo::delete);
    }

    @Transactional
    public void unfollow(Long currentUserId, Long targetUserId) {
        User me = userRepo.findById(currentUserId).orElseThrow();
        User target = userRepo.findById(targetUserId).orElseThrow();

        // Remove my outgoing follow (me -> target)
        followRepo.findByFollowerAndFollowee(me, target).ifPresent(followRepo::delete);

        // Optional: also drop reverse ACCEPTED to "unfriend" behavior (Instagram-like)
        followRepo.findByFollowerAndFollowee(target, me).ifPresent(f -> {
            if (f.getStatus() == FollowStatus.ACCEPTED) {
                followRepo.delete(f);
            }
        });
    }

    // ---- Queries ----

    public List<User> followers(Long userId) {
        User u = userRepo.findById(userId).orElseThrow();
        return followRepo.findAcceptedFollowers(u);
    }

    public List<User> following(Long userId) {
        User u = userRepo.findById(userId).orElseThrow();
        return followRepo.findAcceptedFollowing(u);
    }

    public List<User> friends(Long userId) {
        User u = userRepo.findById(userId).orElseThrow();
        return followRepo.findFriends(u);
    }

    public List<Follow> pendingRequests(Long meId) {
        User me = userRepo.findById(meId).orElseThrow();
        return followRepo.findPendingRequests(me);
    }

    public int pendingCount(Long meId) {
        User me = userRepo.findById(meId).orElseThrow();
        return followRepo.countByFolloweeAndStatus(me, FollowStatus.PENDING);
    }

    // ---- Relationship & Counts ----

    public record Relationship(String outgoing, String incoming) {}

    public Relationship relationship(Long meId, Long otherId) {
        User me = userRepo.findById(meId).orElseThrow();
        User other = userRepo.findById(otherId).orElseThrow();

        String out = followRepo.findByFollowerAndFollowee(me, other)
                .map(f -> f.getStatus().name()).orElse("NONE");  // NONE / PENDING / ACCEPTED
        String in  = followRepo.findByFollowerAndFollowee(other, me)
                .map(f -> f.getStatus().name()).orElse("NONE");
        return new Relationship(out, in);
    }

    public SocialCountsDTO counts(Long userId) {
        User u = userRepo.findById(userId).orElseThrow();
        long followers = followRepo.countAcceptedFollowers(u);
        long following = followRepo.countAcceptedFollowing(u);
        long friends   = followRepo.countFriends(u);
        return new SocialCountsDTO(followers, following, friends);
    }
}
