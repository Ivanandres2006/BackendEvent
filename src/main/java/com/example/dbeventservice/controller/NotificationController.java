package com.example.dbeventservice.controller;

import com.example.dbeventservice.model.FCMToken;
import com.example.dbeventservice.repositories.FCMTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FCMTokenRepository tokenRepo;

    @PostMapping("/register")
    public ResponseEntity<?> registerToken(@RequestBody String token) {
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().body("Invalid token");

        FCMToken fcmToken = new FCMToken();
        fcmToken.setToken(token);
        tokenRepo.save(fcmToken);
        return ResponseEntity.ok("Token saved");
    }

    @PostMapping("/send-test")
    public ResponseEntity<?> sendTestNotification() {
        List<FCMToken> tokens = tokenRepo.findAll();
        for (FCMToken t : tokens) {
            sendPush(t.getToken(), "🎉 WKND", "This is a test notification.");
        }
        return ResponseEntity.ok("Push sent to all devices");
    }

    private void sendPush(String token, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ Notificación enviada: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
