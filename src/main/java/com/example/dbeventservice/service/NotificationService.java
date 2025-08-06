package com.example.dbeventservice.service;

import com.example.dbeventservice.model.FCMToken;
import com.example.dbeventservice.repositories.FCMTokenRepository;
import com.google.firebase.messaging.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class NotificationService {
    private final FCMTokenRepository tokenRepo;

    public NotificationService(FCMTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
        initFirebase();
    }

    public void initFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase_key.json");
                if (serviceAccount == null) {
                    System.out.println("❌ firebase_key.json not found!");
                    return;
                }
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase initialized");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPushToUsername(String username, String title, String body) {
        List<FCMToken> tokens = tokenRepo.findAllByUsername(username);
        for (FCMToken t : tokens) {
            sendPush(t.getToken(), title, body);
        }
    }

    public void sendPush(String token, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
