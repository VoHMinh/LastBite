package com.LastBite.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private Provider provider = Provider.SMTP;
    private String fromName = "LastBite";
    private String fromAddress = "no-reply@mail.lastbite.vn";
    private String replyTo = "";
    private int timeoutMillis = 5000;
    private int maxAttempts = 2;
    private final Resend resend = new Resend();
    private final RateLimit rateLimit = new RateLimit();

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider == null ? Provider.SMTP : provider;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Resend getResend() {
        return resend;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public String formattedFrom() {
        String safeName = isBlank(fromName) ? "LastBite" : fromName.trim();
        String safeAddress = isBlank(fromAddress) ? "no-reply@mail.lastbite.vn" : fromAddress.trim();
        return safeName + " <" + safeAddress + ">";
    }

    public int normalizedMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public int normalizedTimeoutMillis() {
        return Math.max(1000, timeoutMillis);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Provider {
        SMTP,
        RESEND
    }

    public static class Resend {
        private String apiKey = "";
        private String baseUrl = "https://api.resend.com";
        private String webhookSecret = "";
        private boolean requireWebhookSignature = true;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public boolean isRequireWebhookSignature() {
            return requireWebhookSignature;
        }

        public void setRequireWebhookSignature(boolean requireWebhookSignature) {
            this.requireWebhookSignature = requireWebhookSignature;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int perEmailPerMinute = 1;
        private int perEmailPerHour = 5;
        private int perIpPerMinute = 10;
        private int perIpPerHour = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPerEmailPerMinute() {
            return perEmailPerMinute;
        }

        public void setPerEmailPerMinute(int perEmailPerMinute) {
            this.perEmailPerMinute = perEmailPerMinute;
        }

        public int getPerEmailPerHour() {
            return perEmailPerHour;
        }

        public void setPerEmailPerHour(int perEmailPerHour) {
            this.perEmailPerHour = perEmailPerHour;
        }

        public int getPerIpPerMinute() {
            return perIpPerMinute;
        }

        public void setPerIpPerMinute(int perIpPerMinute) {
            this.perIpPerMinute = perIpPerMinute;
        }

        public int getPerIpPerHour() {
            return perIpPerHour;
        }

        public void setPerIpPerHour(int perIpPerHour) {
            this.perIpPerHour = perIpPerHour;
        }
    }
}
