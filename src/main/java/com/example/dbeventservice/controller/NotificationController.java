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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @PostConstruct
    public void scheduleAutoNotification() {
        // ⏰ Hora objetivo: HOY 10:00 AM
        LocalDateTime baseTarget = LocalDateTime.now()
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        LocalDateTime now = LocalDateTime.now();

        // Definimos la hora final sin reasignar
        LocalDateTime finalTarget = now.isAfter(baseTarget)
                ? now.plusMinutes(30)
                : baseTarget;

        long delayMs = ChronoUnit.MILLIS.between(now, finalTarget);

        System.out.println("⏰ Notificación programada para " + finalTarget);

        // Programa el envío
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            System.out.println("⏰ Enviando notificación automática: " + finalTarget);
            List<FCMToken> tokens = tokenRepo.findAll();
            for (FCMToken t : tokens) {
                sendPush(
                        t.getToken(),
                        "🎉 WKND Recordatorio",
                        "¡El evento es hoy! Nos vemos a la 1 PM"
                );
            }
        }, delayMs, TimeUnit.MILLISECONDS);

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

}
