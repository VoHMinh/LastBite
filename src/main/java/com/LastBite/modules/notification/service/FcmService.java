package com.LastBite.modules.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.LastBite.modules.notification.enums.DeviceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FcmService {

    private final FirebaseApp firebaseApp;

    public FcmSendResult sendNotification(String token, DeviceType deviceType, String title, String body,
                                          String imageUrl, String deepLink, Map<String, String> data) {
        try {
            Message message = buildMessage(token, deviceType, title, body, imageUrl, deepLink, data);
            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("FCM sent. messageId={}, token={}, deviceType={}", messageId, maskToken(token), deviceType);
            return FcmSendResult.sent(messageId);
        } catch (FirebaseMessagingException e) {
            handleMessagingException(e, token);
            return FcmSendResult.failed(isRetryableError(e), isInvalidTokenError(e), e.getMessage());
        } catch (Exception e) {
            log.error("FCM send failed. token={}, message={}", maskToken(token), e.getMessage(), e);
            return FcmSendResult.failed(false, false, e.getMessage());
        }
    }

    private Message buildMessage(String token, DeviceType deviceType, String title, String body,
                                 String imageUrl, String deepLink, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .setImage(imageUrl)
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }
        if (deepLink != null && !deepLink.isBlank()) {
            builder.putData("deepLink", deepLink);
        }

        switch (deviceType) {
            case WEB -> builder.setWebpushConfig(WebpushConfig.builder()
                    .setNotification(WebpushNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setImage(imageUrl)
                            .setIcon("/icon-192.png")
                            .build())
                    .setFcmOptions(WebpushFcmOptions.withLink(deepLink == null || deepLink.isBlank() ? "/" : deepLink))
                    .build());
            case IOS -> builder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setAlert(ApsAlert.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build())
                            .setSound("default")
                            .build())
                    .build());
            case ANDROID -> builder.setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .setImage(imageUrl)
                            .build())
                    .build());
        }

        return builder.build();
    }

    public boolean isRetryableError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.INTERNAL
                || errorCode == MessagingErrorCode.QUOTA_EXCEEDED
                || errorCode == MessagingErrorCode.UNAVAILABLE;
    }

    public boolean isInvalidTokenError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private void handleMessagingException(FirebaseMessagingException e, String token) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        if (errorCode == null) {
            log.error("FCM error. token={}, message={}", maskToken(token), e.getMessage());
            return;
        }
        switch (errorCode) {
            case UNREGISTERED, INVALID_ARGUMENT ->
                    log.warn("FCM token invalid. token={}, code={}", maskToken(token), errorCode);
            case QUOTA_EXCEEDED, UNAVAILABLE ->
                    log.warn("FCM temporary failure. token={}, code={}, message={}", maskToken(token), errorCode, e.getMessage());
            default ->
                    log.error("FCM failure. token={}, code={}, message={}", maskToken(token), errorCode, e.getMessage(), e);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    public boolean isAvailable() {
        return firebaseApp != null;
    }
}
