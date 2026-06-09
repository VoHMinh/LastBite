package com.LastBite.modules.notification.service;

public record FcmSendResult(String messageId, boolean retryable, boolean invalidToken, String errorMessage) {

    public static FcmSendResult sent(String messageId) {
        return new FcmSendResult(messageId, false, false, null);
    }

    public static FcmSendResult failed(boolean retryable, boolean invalidToken, String errorMessage) {
        return new FcmSendResult(null, retryable, invalidToken, errorMessage);
    }
}
