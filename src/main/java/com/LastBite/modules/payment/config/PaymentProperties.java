package com.LastBite.modules.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

    private String gateway = "fake";
    private String returnUrl = "http://localhost:3000/orders/payment-return";
    private String cancelUrl = "http://localhost:3000/orders/payment-cancel";
    private int reservationTtlMinutes = 10;
    private final Payos payos = new Payos();

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public int getReservationTtlMinutes() {
        return reservationTtlMinutes;
    }

    public void setReservationTtlMinutes(int reservationTtlMinutes) {
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    public Payos getPayos() {
        return payos;
    }

    public static class Payos {
        private String baseUrl = "https://api-merchant.payos.vn";
        private String clientId = "";
        private String apiKey = "";
        private String checksumKey = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChecksumKey() {
            return checksumKey;
        }

        public void setChecksumKey(String checksumKey) {
            this.checksumKey = checksumKey;
        }
    }
}
