package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowee(User follower, User followee);

    List<Follow> findByFolloweeAndStatus(User followee, FollowStatus status); // followers
    List<Follow> findByFollowerAndStatus(User follower, FollowStatus status); // following

    @Query("SELECT f.followee FROM Follow f WHERE f.follower = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowing(User user);

    @Query("SELECT f.follower FROM Follow f WHERE f.followee = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowers(User user);

    // mutuals = friends
    @Query("""
           SELECT f1.followee FROM Follow f1
           JOIN Follow f2 ON f2.follower = f1.followee AND f2.followee = f1.follower
           WHERE f1.follower = :user AND f1.status = 'ACCEPTED' AND f2.status = 'ACCEPTED'
           """)
    List<User> findFriends(User user);

    // FollowRepository.java
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.followee = :user AND f.status = 'ACCEPTED'")
    long countAcceptedFollowers(@Param("user") User user);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.follower = :user AND f.status = 'ACCEPTED'")
    long countAcceptedFollowing(@Param("user") User user);

    @Query("""
       SELECT COUNT(f1) FROM Follow f1
       JOIN Follow f2 ON f2.follower = f1.followee AND f2.followee = f1.follower
       WHERE f1.follower = :user AND f1.status = 'ACCEPTED' AND f2.status = 'ACCEPTED'
       """)
    long countFriends(@Param("user") User user);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowerId(Long followerId);
    long countByFollowingId(Long followingId);

    // NEW: incoming pending for a user (the one being followed)
    List<Follow> findByFollowingIdAndStatusOrderByCreatedAtDesc(Long followingId, FollowStatus status, Pageable pageable);

    // NEW: allow the "followed" user to remove a pending request
    void deleteByFollowerIdAndFollowingIdAndStatus(Long followerId, Long followingId, FollowStatus status);


    // existence / edges
    boolean existsByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);
    boolean existsByFollower_IdAndFollowee_IdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    void deleteByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);
    void deleteByFollower_IdAndFollowee_IdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    // counts
    long countByFollower_Id(Long followerId);
    long countByFollowee_Id(Long followeeId);

    // lists (filter by status when needed)
    List<Follow> findByFollower_IdOrderByIdDesc(Long followerId, Pageable pageable);
    List<Follow> findByFollowee_IdOrderByIdDesc(Long followeeId, Pageable pageable);

    List<Follow> findByFollower_IdAndStatusOrderByIdDesc(Long followerId, FollowStatus status, Pageable pageable);
    List<Follow> findByFollowee_IdAndStatusOrderByIdDesc(Long followeeId, FollowStatus status, Pageable pageable);

    // for "incoming requests" (PENDING to me = I'm the followee)
    default List<Follow> incomingPending(Long meId, Pageable pageable) {
        return findByFollowee_IdAndStatusOrderByIdDesc(meId, FollowStatus.PENDING, pageable);
    }
}
