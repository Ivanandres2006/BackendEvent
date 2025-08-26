package com.example.dbeventservice.service;

import com.example.dbeventservice.model.FCMToken;
import com.example.dbeventservice.model.User;
import com.example.dbeventservice.repositories.FCMTokenRepository;
import com.example.dbeventservice.controller.NotificationController; // you already treat it like a service
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialNotificationService {

    private final FCMTokenRepository tokenRepo;
    private final NotificationController notificationController;

    public void notifyFollowRequest(User follower, User followee) {
        String title = "New follow request";
        String body  = dn(follower) + " wants to follow you";
        tokenRepo.findAllByUsername(followee.getUsername())
                .forEach(t -> notificationController.sendPushWithSocial(
                        t.getToken(), title, body, follower.getId(), follower.getUsername(), "FOLLOW_REQUEST"));
    }

    /** Sent to the follower whose request just got accepted (by acceptor). */
    public void notifyAccepted(User acceptor, User followerWhoGetsAccepted) {
        String title = "Request accepted";
        String body  = dn(acceptor) + " accepted your follow request";
        tokenRepo.findAllByUsername(followerWhoGetsAccepted.getUsername())
                .forEach(t -> notificationController.sendPushWithSocial(
                        t.getToken(), title, body, acceptor.getId(), acceptor.getUsername(), "FOLLOW_ACCEPTED"));
    }

    /** Optional Instagram-like: tell the followee they now have a follower. */
    public void notifyFollowedYou(User follower, User followee) {
        String title = "New follower";
        String body  = dn(follower) + " started following you";
        tokenRepo.findAllByUsername(followee.getUsername())
                .forEach(t -> notificationController.sendPushWithSocial(
                        t.getToken(), title, body, follower.getId(), follower.getUsername(), "FOLLOWED_YOU"));
    }

    private String dn(User u) {
        String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String ln = u.getLastName() == null ? "" : u.getLastName().trim();
        String dn = (fn + " " + ln).trim();
        return dn.isBlank() ? u.getUsername() : dn;
    }
}
