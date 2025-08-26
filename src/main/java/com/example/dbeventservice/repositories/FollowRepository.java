package com.example.dbeventservice.repositories;

import com.example.dbeventservice.model.Enum.FollowStatus;
import com.example.dbeventservice.model.Follow;
import com.example.dbeventservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    // Direct lookups
    Optional<Follow> findByFollowerAndFollowee(User follower, User followee);

    // Generic helpers (optional but handy)
    List<Follow> findByFolloweeAndStatus(User followee, FollowStatus status);
    List<Follow> findByFollowerAndStatus(User follower, FollowStatus status);

    // ----- Lists as Users -----

    // Who I follow (ACCEPTED)
    @Query("SELECT f.followee FROM Follow f WHERE f.follower = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowing(@Param("user") User user);

    // Who follows me (ACCEPTED)
    @Query("SELECT f.follower FROM Follow f WHERE f.followee = :user AND f.status = 'ACCEPTED'")
    List<User> findAcceptedFollowers(@Param("user") User user);

    // Mutuals (friends) = both directions ACCEPTED
    @Query("""
           SELECT f1.followee FROM Follow f1
           JOIN Follow f2 ON f2.follower = f1.followee AND f2.followee = f1.follower
           WHERE f1.follower = :user AND f1.status = 'ACCEPTED' AND f2.status = 'ACCEPTED'
           """)
    List<User> findFriends(@Param("user") User user);

    // ----- Counts -----

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

    // ----- Pending requests -----

    // Incoming PENDING requests to me (I'm the followee)
    @Query("SELECT f FROM Follow f WHERE f.followee = :user AND f.status = 'PENDING' ORDER BY f.id DESC")
    List<Follow> findPendingRequests(@Param("user") User user);

    // Utility used by service for badge counts
    int countByFolloweeAndStatus(User followee, FollowStatus status);
}
