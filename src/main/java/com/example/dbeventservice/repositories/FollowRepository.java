package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    // Direct entity lookups (fine to keep)
    Optional<Follow> findByFollowerAndFollowee(User follower, User followee);

    // Followers of X (X is followee), Following of X (X is follower)
    List<Follow> findByFolloweeAndStatus(User followee, FollowStatus status);
    List<Follow> findByFollowerAndStatus(User follower, FollowStatus status);

    // Accepted lists as Users
    @Query("SELECT f.followee FROM Follow f WHERE f.follower = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowing(@Param("user") User user);

    @Query("SELECT f.follower FROM Follow f WHERE f.followee = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowers(@Param("user") User user);

    // mutuals = friends
    @Query("""
           SELECT f1.followee FROM Follow f1
           JOIN Follow f2 ON f2.follower = f1.followee AND f2.followee = f1.follower
           WHERE f1.follower = :user AND f1.status = 'ACCEPTED' AND f2.status = 'ACCEPTED'
           """)
    List<User> findFriends(@Param("user") User user);

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

    // ---------- CORRECT nested-id methods (use these instead of the old ones) ----------
    boolean existsByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);
    boolean existsByFollower_IdAndFollowee_IdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    void deleteByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);
    void deleteByFollower_IdAndFollowee_IdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    long countByFollower_Id(Long followerId);  // how many I follow
    long countByFollowee_Id(Long followeeId);  // how many follow me

    List<Follow> findByFollower_IdOrderByIdDesc(Long followerId, Pageable pageable);
    List<Follow> findByFollowee_IdOrderByIdDesc(Long followeeId, Pageable pageable);

    List<Follow> findByFollower_IdAndStatusOrderByIdDesc(Long followerId, FollowStatus status, Pageable pageable);
    List<Follow> findByFollowee_IdAndStatusOrderByIdDesc(Long followeeId, FollowStatus status, Pageable pageable);

    // Incoming PENDING requests to me (I'm the followee)
    default List<Follow> incomingPending(Long meId, Pageable pageable) {
        return findByFollowee_IdAndStatusOrderByIdDesc(meId, FollowStatus.PENDING, pageable);
    }


    @Query("select f from Follow f where f.followee = :user and f.status = 'PENDING'")
    List<Follow> findPendingRequests(@Param("user") User user);

    int countByFolloweeAndStatus(User followee, FollowStatus status);

}
