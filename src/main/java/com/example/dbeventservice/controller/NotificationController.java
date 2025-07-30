package com.example.dbeventservice.controller;

import com.example.dbeventservice.model.FCMToken;
import com.example.dbeventservice.repositories.FCMTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FCMTokenRepository tokenRepo;

    @PostMapping("/register")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");

        System.out.println("📥 TOKEN RECIBIDO: " + token);
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Invalid token");
        }

        token = token.trim();

        // Evita duplicados
        if (tokenRepo.findByToken(token).isEmpty()) {
            FCMToken fcmToken = new FCMToken();
            fcmToken.setToken(token);
            tokenRepo.save(fcmToken);
            System.out.println("✅ Nuevo token guardado en BD");
        } else {
            System.out.println("ℹ️ Token ya existe en BD, no se duplica");
        }

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
        System.out.println("🔥 Sending to token: [" + token + "]");
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

        } catch (FirebaseMessagingException e) {
            System.out.println("❌ FCM failed:");
            System.out.println("   • Error code: " + e.getMessagingErrorCode());
            System.out.println("   • HTTP status: " + (e.getHttpResponse() != null ? e.getHttpResponse().getStatusCode() : "n/a"));
            System.out.println("   • Response body: " + (e.getHttpResponse() != null ? e.getHttpResponse().getContent() : "n/a"));

            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                    e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                System.out.println("🧹 Removing invalid FCM token from DB: " + token);
                try { tokenRepo.deleteByToken(token); } catch (Exception ignore) {}
            }

        } catch (Exception e) {
            System.out.println("❌ Unexpected exception while sending push:");
            e.printStackTrace();
        }
    }

}
