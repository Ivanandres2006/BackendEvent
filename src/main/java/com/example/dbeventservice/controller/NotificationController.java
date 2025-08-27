package com.example.dbeventservice.controller;

import com.example.dbeventservice.model.FCMToken;
import com.example.dbeventservice.repositories.FCMTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FCMTokenRepository tokenRepo;

    @PostConstruct
    public void initFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("📦 Initializing Firebase manually...");
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
            System.out.println("❌ Firebase init error:");
            e.printStackTrace();
        }
    }


    @PostMapping("/register")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String username = body.get("username"); // 👈 NEW

        System.out.println("📥 TOKEN RECIBIDO: " + token + " for user: " + username);
        if (token == null || token.isBlank() || username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("Invalid token or username");
        }

        token = token.trim();
        username = username.trim();

        // Save or update token for this user
        Optional<FCMToken> existing = tokenRepo.findByToken(token);
        if (existing.isEmpty()) {
            FCMToken fcmToken = new FCMToken();
            fcmToken.setToken(token);
            fcmToken.setUsername(username); // Save user info!
            tokenRepo.save(fcmToken);
            System.out.println("✅ Nuevo token guardado en BD");
        } else {
            FCMToken fcmToken = existing.get();
            fcmToken.setUsername(username); // Update username in case user logged in with different account
            tokenRepo.save(fcmToken);
            System.out.println("ℹ️ Token ya existe, username actualizado");
        }

        return ResponseEntity.ok("Token saved");
    }

    @PostMapping("/send-test")
    public ResponseEntity<?> sendTestNotification() {
        List<FCMToken> tokens = tokenRepo.findAll();
        System.out.println("📡 Sending test notification to " + tokens.size() + " tokens...");

        for (FCMToken t : tokens) {
            System.out.println("🔑 Trying token: " + t.getToken());
            sendPush(t.getToken(), "🎉 WKND", "This is a test notification.");
        }

        return ResponseEntity.ok("Push attempted for all tokens");
    }


    private void sendPush(String token, String title, String body) {
        System.out.println("🔥 Sending to token: [" + token + "]");

        try {
            // ✅ Ensure Firebase is initialized (fallback if needed)
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("⚠️ FirebaseApp not initialized. Initializing now...");
                InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase_key.json");

                if (serviceAccount == null) {
                    System.out.println("❌ firebase_key.json not found in resources!");
                    return;
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                System.out.println("✅ FirebaseApp initialized successfully.");
                System.out.println("📛 FirebaseApp name: " + FirebaseApp.getInstance().getName());
                System.out.println("🌍 FirebaseApp options projectId: " + FirebaseApp.getInstance().getOptions().getProjectId());

            }

            // ✅ Build and send message
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ Notificación enviada: " + response);
            System.out.println("📲 Push success for token: " + token);

        } catch (FirebaseMessagingException e) {
            System.out.println("❌ FCM failed:");
            System.out.println("   • Error code: " + e.getMessagingErrorCode());
            System.out.println("   • HTTP status: " + (e.getHttpResponse() != null ? e.getHttpResponse().getStatusCode() : "n/a"));
            System.out.println("   • Response body: " + (e.getHttpResponse() != null ? e.getHttpResponse().getContent() : "n/a"));

            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                    e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                System.out.println("🧹 Removing invalid FCM token from DB: " + token);
                try {
                    tokenRepo.deleteByToken(token);
                } catch (Exception ignore) {}
            }

        } catch (Exception e) {
            System.out.println("❌ Unexpected exception while sending push:");
            e.printStackTrace();
        }
    }

    // NEW: send social push to a specific token, carrying data payload
    public void sendPushWithSocial(String token,
                                   String title,
                                   String body,
                                   Long actorId,
                                   String actorUsername,
                                   String kind) {
        try {
            if (FirebaseApp.getApps().isEmpty()) initFirebase(); // reuse your init

            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", "SOCIAL")                 // <-- iOS will branch on this
                    .putData("kind", kind)                     // FOLLOW_REQUEST / FOLLOW_ACCEPTED / FOLLOWED_YOU
                    .putData("actorId", String.valueOf(actorId))
                    .putData("actorUsername", actorUsername == null ? "" : actorUsername)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ Social push sent: " + response);
        } catch (Exception e) {
            System.out.println("❌ Social push failed:");
            e.printStackTrace();
        }
    }

    // src/main/java/com/example/dbeventservice/controller/NotificationController.java
// (additions only; keep your existing methods)

    public void sendPushWithData(String token, String title, String body, Map<String,String> data) {
        try {
            if (FirebaseApp.getApps().isEmpty()) initFirebaseIfNeeded();

            Message.Builder b = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());

            if (data != null) data.forEach(b::putData);

            String response = FirebaseMessaging.getInstance().send(b.build());
            System.out.println("✅ Push (data) sent: " + response);
        } catch (Exception e) {
            System.out.println("❌ sendPushWithData failed");
            e.printStackTrace();
        }
    }

    private void initFirebaseIfNeeded() throws Exception {
        InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase_key.json");
        if (serviceAccount == null) throw new IllegalStateException("firebase_key.json missing");
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount)).build();
        FirebaseApp.initializeApp(options);
    }

    public void sendPushToUsernameWithData(String username, String title, String body, Map<String,String> data) {
        List<FCMToken> tokens = tokenRepo.findAllByUsername(username);
        for (FCMToken t : tokens) {
            sendPushWithData(t.getToken(), title, body, data);
        }
    }

    public void sendPushToUsernamesWithData(List<String> usernames, String title, String body, Map<String,String> data) {
        for (String u : usernames) {
            sendPushToUsernameWithData(u, title, body, data);
        }
    }


    // NEW: send to all tokens for a username
    public void sendSocialToUsername(String username,
                                     String title,
                                     String body,
                                     Long actorId,
                                     String actorUsername,
                                     String kind) {
        List<FCMToken> tokens = tokenRepo.findAllByUsername(username);
        for (FCMToken t : tokens) {
            sendPushWithSocial(t.getToken(), title, body, actorId, actorUsername, kind);
        }
    }

    public void sendPushToUsername(String username, String title, String body) {
        List<FCMToken> tokens = tokenRepo.findAllByUsername(username);
        for (FCMToken token : tokens) {
            sendPush(token.getToken(), title, body);
        }
    }


    public void sendPushWithEvent(String token, String title, String body, Long eventId) {
        try {
            // Ensure Firebase is initialized (init if not)
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("⚠️ FirebaseApp not initialized. Initializing now...");
                InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase_key.json");
                if (serviceAccount == null) {
                    System.out.println("❌ firebase_key.json not found in resources!");
                    return;
                }
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("✅ FirebaseApp initialized successfully.");
            }

            // Build the message with deep link eventId
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("eventId", eventId.toString())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ Notificación enviada a: " + token + " — response: " + response);

        } catch (FirebaseMessagingException e) {
            System.out.println("❌ FCM failed:");
            System.out.println("   • Error code: " + e.getMessagingErrorCode());
            System.out.println("   • HTTP status: " + (e.getHttpResponse() != null ? e.getHttpResponse().getStatusCode() : "n/a"));
            System.out.println("   • Response body: " + (e.getHttpResponse() != null ? e.getHttpResponse().getContent() : "n/a"));
        } catch (Exception e) {
            System.out.println("❌ Unexpected exception while sending push:");
            e.printStackTrace();
        }
    }

}
