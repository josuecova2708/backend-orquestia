package com.orquestia.notificacion;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Envía push notifications via Firebase Cloud Messaging.
 * En Cloud Run usa Application Default Credentials automáticamente.
 * En local necesita GOOGLE_APPLICATION_CREDENTIALS apuntando a un service account JSON.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    @PostConstruct
    public void init() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId("project-f11e5e0e-e3c4-4083-bb6")
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized");
            } catch (IOException e) {
                log.warn("Firebase Admin SDK not initialized (no credentials): {}", e.getMessage());
            }
        }
    }

    public void sendPush(List<String> deviceTokens, String title, String body, Map<String, String> data) {
        if (deviceTokens == null || deviceTokens.isEmpty()) return;
        if (FirebaseApp.getApps().isEmpty()) return;

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .addAllTokens(deviceTokens)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("orquestia_channel")
                                    .build())
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM sent: {} success, {} failure", response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("FCM FirebaseMessagingException: {} {}", e.getMessagingErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("FCM unexpected error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
