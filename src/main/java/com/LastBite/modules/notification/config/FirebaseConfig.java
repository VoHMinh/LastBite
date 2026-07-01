package com.LastBite.modules.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    @Value("${app.fcm.credentials-path:}")
    private String credentialsPath;

    @Value("${app.fcm.credentials-base64:}")
    private String credentialsBase64;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (InputStream credentials = openCredentials()) {
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(credentials);
            
            // Re-open stream to extract project_id (first stream was consumed by GoogleCredentials)
            String projectId = extractProjectId();
            
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(googleCredentials)
                    .setProjectId(projectId)
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase initialized for project {}", app.getOptions().getProjectId());
            return app;
        }
    }
    
    private String extractProjectId() throws IOException {
        try (InputStream is = openCredentials()) {
            // Use Jackson (already in Spring Boot) to parse project_id from service account JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(is);
            com.fasterxml.jackson.databind.JsonNode projectIdNode = root.get("project_id");
            if (projectIdNode != null && !projectIdNode.isNull()) {
                return projectIdNode.asText();
            }
            throw new IOException("project_id not found in Firebase service account JSON");
        }
    }

    private InputStream openCredentials() throws IOException {
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(credentialsBase64.trim()));
        }
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            String path = credentialsPath.trim();
            if (path.startsWith("classpath:")) {
                return new ClassPathResource(path.substring("classpath:".length())).getInputStream();
            }
            return new FileInputStream(path);
        }
        String dockerPath = "/app/firebase-credentials.json";
        if (new java.io.File(dockerPath).exists()) {
            return new FileInputStream(dockerPath);
        }
        throw new IOException("Missing Firebase credentials. Set APP_FCM_CREDENTIALS_BASE64, APP_FCM_CREDENTIALS_PATH, or mount credentials at /app/keys/firebase-credentials.json.");
    }
}
